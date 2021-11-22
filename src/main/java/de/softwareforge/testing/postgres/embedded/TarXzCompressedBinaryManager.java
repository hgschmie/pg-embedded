/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.softwareforge.testing.postgres.embedded;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.XZInputStream;

/**
 * Loads a native binary installation and returns the location of it.
 */
public final class TarXzCompressedBinaryManager implements NativeBinaryManager {

    private static final Logger LOG = LoggerFactory.getLogger(TarXzCompressedBinaryManager.class);

    private static final String INSTALL_DIRECTORY_PREFIX = "PG-";

    private static final Map<Supplier<InputStream>, File> KNOWN_INSTALLATIONS = new ConcurrentHashMap<>();

    private final Lock prepareBinariesLock = new ReentrantLock();

    private final File installationBaseDirectory;
    private final String lockFileName;
    private final Supplier<InputStream> inputStreamLocator;

    /**
     * Creates a new binary manager for tar-xz compressed archives.
     * <p>
     * The implementation of {@link Supplier<InputStream>} to locate the stream that gets unpacked must satisfy the following criteria:
     * <ul>
     *     <li>It must implement {@link #equals(Object)} and {@link #hashCode()}.</li>
     *     <li>It should implement {@link #toString()} to return meaningful information about the locator.</li>
     *     <li>It must allow multiple calls to {@link Supplier#get()} which all return the same, byte-identical contents.
     *     The operation should be cheap as it may be called multiple times.</li>
     * </ul>
     *
     * @param installationBaseDirectory Base directory in which the binary distribution is unpacked. Must not be null.
     * @param lockFileName              Name of a file to use as file lock when unpacking the disttribution.
     * @param inputStreamLocator        An implementation of {@link Supplier<InputStream>} that satisfies the conditions above. Must not be null.
     */
    public TarXzCompressedBinaryManager(@Nonnull File installationBaseDirectory,
            @Nonnull String lockFileName,
            @Nonnull Supplier<InputStream> inputStreamLocator) {
        this.installationBaseDirectory = checkNotNull(installationBaseDirectory, "installationBaseDirectory is null");
        this.lockFileName = checkNotNull(lockFileName, "lockFileName is null");
        this.inputStreamLocator = checkNotNull(inputStreamLocator, "inputStreamLocator is null");

        checkState(this.installationBaseDirectory.setWritable(true, false),
                "Could not make install base directory %s writable!", this.installationBaseDirectory);
    }

    @Override
    public File getLocation() throws IOException {

        File installationDirectory = KNOWN_INSTALLATIONS.get(inputStreamLocator);
        if (installationDirectory != null && installationDirectory.exists()) {
            return installationDirectory;
        }

        prepareBinariesLock.lock();
        try {
            try (InputStream installationArchive = inputStreamLocator.get()) {
                checkState(installationArchive != null, "Locator '%s' did not find a suitable archive to unpack!", inputStreamLocator);
                try (DigestInputStream archiveDigestStream = new DigestInputStream(installationArchive, MessageDigest.getInstance("MD5"))) {
                    ByteStreams.exhaust(archiveDigestStream);
                    String installationDigest = BaseEncoding.base16().encode(archiveDigestStream.getMessageDigest().digest());
                    installationDirectory = new File(installationBaseDirectory, INSTALL_DIRECTORY_PREFIX + installationDigest);
                    EmbeddedUtil.mkdirs(installationDirectory);
                }
            }

            final File unpackLockFile = new File(installationDirectory, lockFileName);
            final File installationExistsFile = new File(installationDirectory, ".exists");

            if (!installationExistsFile.exists()) {
                try (FileOutputStream lockStream = new FileOutputStream(unpackLockFile);
                        FileLock unpackLock = lockStream.getChannel().tryLock()) {
                    if (unpackLock != null) {
                        checkState(!installationExistsFile.exists(), "unpack lock acquired but .exists file is present " + installationExistsFile);
                        LOG.info("extracting archive...");
                        try (InputStream archiveStream = inputStreamLocator.get()) {
                            extractTxz(archiveStream, installationDirectory.getPath());
                            checkState(installationExistsFile.createNewFile(), "couldn't create %s file!", installationExistsFile);
                        }
                    } else {
                        // the other guy is unpacking for us.
                        int maxAttempts = 60;
                        while (!installationExistsFile.exists() && --maxAttempts > 0) { // NOPMD
                            Thread.sleep(1000L);
                        }
                        checkState(installationExistsFile.exists(), "Waited 60 seconds for archive to be unpacked but it never finished!");
                    }
                } finally {
                    if (unpackLockFile.exists() && !unpackLockFile.delete()) {
                        LOG.error(format("could not remove lock file %s", unpackLockFile.getAbsolutePath()));
                    }
                }
            }

            KNOWN_INSTALLATIONS.putIfAbsent(inputStreamLocator, installationDirectory);
            LOG.debug(format("Unpacked archive at %s", installationDirectory));
            return installationDirectory;

        } catch (final NoSuchAlgorithmException e) {
            throw new IOException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } finally {
            prepareBinariesLock.lock();
        }
    }

    /**
     * Unpack archive compressed by tar with xz compression.
     *
     * @param stream    A tar-xz compressed data stream.
     * @param targetDir The directory to extract the content to.
     */
    private static void extractTxz(InputStream stream, String targetDir) throws IOException {
        try (XZInputStream xzIn = new XZInputStream(stream);
                TarArchiveInputStream tarIn = new TarArchiveInputStream(xzIn)) {
            final Phaser phaser = new Phaser(1);
            TarArchiveEntry entry;

            while ((entry = tarIn.getNextTarEntry()) != null) { //NOPMD
                final String individualFile = entry.getName();
                final File fsObject = new File(targetDir, individualFile);
                final Path fsPath = fsObject.toPath();
                if (Files.exists(fsPath, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(fsPath, LinkOption.NOFOLLOW_LINKS)) {
                    Files.delete(fsPath);
                    LOG.debug(format("Deleting existing entry %s", fsPath));
                }

                if (entry.isSymbolicLink() || entry.isLink()) {
                    Path target = FileSystems.getDefault().getPath(entry.getLinkName());
                    Files.createSymbolicLink(fsPath, target);
                } else if (entry.isFile()) {
                    byte[] content = new byte[(int) entry.getSize()];
                    int read = tarIn.read(content, 0, content.length);
                    checkState(read != -1, "could not read %s", individualFile);
                    EmbeddedUtil.mkdirs(fsObject.getParentFile());

                    final AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(fsPath, CREATE, WRITE); //NOPMD
                    final ByteBuffer buffer = ByteBuffer.wrap(content); //NOPMD

                    phaser.register();
                    fileChannel.write(buffer, 0, fileChannel, new CompletionHandler<Integer, Channel>() {
                        @Override
                        public void completed(Integer written, Channel channel) {
                            closeChannel(channel);
                        }

                        @Override
                        public void failed(Throwable error, Channel channel) {
                            LOG.error(format("could not write file %s", fsObject.getAbsolutePath()), error);
                            closeChannel(channel);
                        }

                        private void closeChannel(Channel channel) {
                            try {
                                channel.close();
                            } catch (IOException e) {
                                LOG.error("While closing channel:", e);
                            } finally {
                                phaser.arriveAndDeregister();
                            }
                        }
                    });
                } else if (entry.isDirectory()) {
                    EmbeddedUtil.mkdirs(fsObject);
                } else {
                    throw new IOException(format("Unsupported entry in tar file found: %s", individualFile));
                }

                if (individualFile.startsWith("bin/") || individualFile.startsWith("./bin/")) {
                    if (!fsObject.setExecutable(true, false)) {
                        throw new IOException(format("Could not make %s executable!", individualFile));
                    }
                }
            }

            phaser.arriveAndAwaitAdvance();
        }
    }
}
