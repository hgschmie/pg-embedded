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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.XZInputStream;

final class EmbeddedUtil {

    static final Logger LOG = LoggerFactory.getLogger(EmbeddedUtil.class);

    static final String OS_NAME;
    static final String OS_ARCH;
    static final boolean IS_OS_WINDOWS;
    static final boolean IS_OS_MAC;
    static final boolean IS_OS_LINUX;

    private static final String ALPHANUM;
    private static final String LOWERCASE;

    static {
        OS_NAME = getSystemProperty("os.name");
        OS_ARCH = getSystemProperty("os.arch");

        IS_OS_WINDOWS = getOsMatchesName("Windows");
        IS_OS_LINUX = getOsMatchesName("Linux");
        IS_OS_MAC = getOsMatchesName("Mac");

        String numbers = sequence('0', 10);
        LOWERCASE = sequence('a', 26);
        String uppercase = sequence('A', 26);
        ALPHANUM = numbers + LOWERCASE + uppercase;
    }

    private EmbeddedUtil() {
        throw new AssertionError("EmbeddedUtil can not be instantiated");
    }

    static File getWorkingDirectory() {
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        return new File(tmpDir, "embedded-pg");
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
            checkArgument(path.charAt(i) != 0,
                    "Null byte present in file/path name.");
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

    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    static void rmdirs(File dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    static String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        long hours = 0;
        long minutes = 0;
        long secs = 0;
        long ms = 0;
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        if (millis == 0) {
            builder.add("0 ms");
        } else {
            long seconds = millis / 1000L;
            hours = seconds / 3600L;
            minutes = (seconds % 3600L) / 60L;
            secs = (seconds % 60L);
            ms = millis % 1000L;

            if (hours != 0) {
                builder.add(hours + " hours");
            }
            if (minutes != 0) {
                builder.add(minutes + " minutes");
            }
            if (secs != 0) {
                builder.add(secs + " seconds");
            }
            if (ms != 0) {
                builder.add(ms + " ms");
            }
        }

        return Joiner.on(' ').join(builder.build());
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
     * Get current operating system string. The string is used in the appropriate postgres archive name.
     *
     * @return Current operating system string.
     */
    static String getOS() {
        if (IS_OS_WINDOWS) {
            return "windows";
        } else if (IS_OS_MAC) {
            return "darwin";
        } else if (IS_OS_LINUX) {
            return "linux";
        } else {
            throw new UnsupportedOperationException("Unknown OS: " + OS_NAME);
        }
    }

    /**
     * Get the machine architecture string. The string is used in the appropriate postgres archive name.
     *
     * @return Current machine architecture string.
     */
    static String getArchitecture() {
        return "amd64".equals(OS_ARCH) ? "x86_64" : OS_ARCH;
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
                final Path fsPath = fsObject.toPath();
                if (Files.exists(fsPath, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(fsPath, LinkOption.NOFOLLOW_LINKS)) {
                    Files.delete(fsPath);
                    LOG.debug("Deleting existing entry %s", fsPath);
                }

                if (entry.isSymbolicLink() || entry.isLink()) {
                    Path target = FileSystems.getDefault().getPath(entry.getLinkName());
                    Files.createSymbolicLink(fsPath, target);
                } else if (entry.isFile()) {

                    byte[] content = new byte[(int) entry.getSize()];
                    int read = tarIn.read(content, 0, content.length);
                    checkState(read != -1, "could not read %s", individualFile);
                    mkdirs(fsObject.getParentFile());

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

    private static String sequence(char start, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append((char) (start + i));
        }
        return sb.toString();
    }

    static String randomAlphaNumeric(int length) {
        return randomString(ALPHANUM, length);
    }

    static String randomLowercase(int length) {
        return randomString(LOWERCASE, length);
    }

    private static String randomString(String alphabet, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int random = ThreadLocalRandom.current().nextInt(alphabet.length());
            sb.append(alphabet.charAt(random));
        }
        return sb.toString();
    }

    private static String getSystemProperty(String propertyName) {
        try {
            return Strings.nullToEmpty(System.getProperty(propertyName, ""));
        } catch (SecurityException e) {
            return "<unknown>";
        }
    }

    private static boolean getOsMatchesName(final String osNamePrefix) {
        return OS_NAME.toLowerCase(Locale.ROOT).startsWith(osNamePrefix.toLowerCase(Locale.ROOT));
    }
}
