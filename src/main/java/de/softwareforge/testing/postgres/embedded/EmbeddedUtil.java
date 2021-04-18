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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channel;
import java.nio.channels.CompletionHandler;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.Phaser;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.XZInputStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

final class EmbeddedUtil {

    static final Logger LOG = LoggerFactory.getLogger(EmbeddedUtil.class);

    private EmbeddedUtil() {
        throw new AssertionError("EmbeddedUtil can not be instantiated");
    }

    static File getWorkingDirectory() {
        return new File(SystemUtils.getJavaIoTmpDir(), "embedded-pg");
    }

    //
    // taken from apache commons io
    //
    static String getFileBaseName(final String fileName) {
        if (fileName == null) {
            return null;
        }
        failIfNullBytePresent(fileName);
        final int index = indexOfLastSeparator(fileName);
        return fileName.substring(index + 1);
    }

    private static void failIfNullBytePresent(final String path) {
        final int len = path.length();
        for (int i = 0; i < len; i++) {
            checkArgument(path.charAt(i) != 0, "Null byte present in file/path name. There are no " +
                    "known legitimate use cases for such data, but several injection attacks may use it");
        }
    }

    private static int indexOfLastSeparator(final String fileName) {
        if (fileName == null) {
            return -1;
        }
        final int lastUnixPos = fileName.lastIndexOf('/'); // unix
        final int lastWindowsPos = fileName.lastIndexOf('\\'); // windows
        return Math.max(lastUnixPos, lastWindowsPos);
    }

    //
    // taken from apache commons io
    //

    static void mkdirs(File dir) {
        if (!dir.mkdirs() && !(dir.isDirectory() && dir.exists())) {
            throw new IllegalStateException("could not create " + dir);
        }
    }

    static void rmdirs(File dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    static String formatDuration(Duration duration) {
        return DurationFormatUtils.formatDuration(duration.toMillis(), "m' minutes 's' seconds 'S' ms'");
    }

    static int allocatePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            while (!socket.isBound()) {
                Thread.sleep(50);
            }
            return socket.getLocalPort();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Thread interrupted!", e);
        }
    }

    /**
     * Get current operating system string. The string is used in the appropriate postgres binary name.
     *
     * @return Current operating system string.
     */
    static String getOS() {
        if (SystemUtils.IS_OS_WINDOWS) {
            return "windows";
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            return "darwin";
        } else if (SystemUtils.IS_OS_LINUX) {
            return "linux";
        } else {
            throw new UnsupportedOperationException("Unknown OS " + SystemUtils.OS_NAME);
        }
    }

    /**
     * Get the machine architecture string. The string is used in the appropriate postgres binary name.
     *
     * @return Current machine architecture string.
     */
    static String getArchitecture() {
        return "amd64".equals(SystemUtils.OS_ARCH) ? "x86_64" : SystemUtils.OS_ARCH;
    }

    /**
     * Unpack archive compressed by tar with xz compression. By default system tar is used (faster). If not found, then the java implementation takes place.
     *
     * @param stream    A stream with the postgres binaries.
     * @param targetDir The directory to extract the content to.
     */
    static void extractTxz(InputStream stream, String targetDir) throws IOException {
        try (XZInputStream xzIn = new XZInputStream(stream);
                TarArchiveInputStream tarIn = new TarArchiveInputStream(xzIn)) {
            final Phaser phaser = new Phaser(1);
            TarArchiveEntry entry;

            while ((entry = tarIn.getNextTarEntry()) != null) { //NOPMD
                final String individualFile = entry.getName();
                final File fsObject = new File(targetDir, individualFile);

                if (entry.isSymbolicLink() || entry.isLink()) {
                    Path target = FileSystems.getDefault().getPath(entry.getLinkName());
                    Files.createSymbolicLink(fsObject.toPath(), target);
                } else if (entry.isFile()) {
                    byte[] content = new byte[(int) entry.getSize()];
                    int read = tarIn.read(content, 0, content.length);
                    checkState(read != -1, "could not read %s", individualFile);
                    mkdirs(fsObject.getParentFile());

                    final AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(fsObject.toPath(), CREATE, WRITE); //NOPMD
                    final ByteBuffer buffer = ByteBuffer.wrap(content); //NOPMD

                    phaser.register();
                    fileChannel.write(buffer, 0, fileChannel, new CompletionHandler<Integer, Channel>() {
                        @Override
                        public void completed(Integer written, Channel channel) {
                            closeChannel(channel);
                        }

                        @Override
                        public void failed(Throwable error, Channel channel) {
                            LOG.error("could not write file " + fsObject.getAbsolutePath(), error);
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
                    mkdirs(fsObject);
                } else {
                    throw new UnsupportedOperationException(format("unsupported entry found: %s", individualFile)
                    );
                }

                if (individualFile.startsWith("bin/") || individualFile.startsWith("./bin/")) {
                    fsObject.setExecutable(true, false);
                }
            }

            phaser.arriveAndAwaitAdvance();
        }
    }
}
