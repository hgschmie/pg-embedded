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

import jakarta.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

final class EmbeddedUtil {

    static final String OS_NAME;
    static final String OS_ARCH;

    static final boolean IS_OS_WINDOWS;
    static final boolean IS_OS_MAC;
    static final boolean IS_OS_LINUX;
    static final boolean IS_ALPINE_LINUX;

    static final boolean IS_ARCH_X86_64;
    static final boolean IS_ARCH_AARCH64;
    static final boolean IS_ARCH_AARCH32;

    private static final String ALPHANUM;
    private static final String LOWERCASE;

    static {
        OS_NAME = getSystemProperty("os.name");
        OS_ARCH = getSystemProperty("os.arch");

        IS_OS_WINDOWS = getOsMatchesName("Windows");
        IS_OS_LINUX = getOsMatchesName("Linux");
        IS_OS_MAC = getOsMatchesName("Mac");

        IS_ARCH_X86_64 = OS_ARCH.equals("x86_64") || OS_ARCH.equals("amd64");
        IS_ARCH_AARCH64 = OS_ARCH.equals("aarch64");
        IS_ARCH_AARCH32 = OS_ARCH.equals("aarch32") || OS_ARCH.equals("arm");

        // this is a glorious hack
        IS_ALPINE_LINUX = new File("/etc/alpine-release").exists();

        String numbers = sequence('0', 10);
        LOWERCASE = sequence('a', 26);
        String uppercase = sequence('A', 26);
        ALPHANUM = numbers + LOWERCASE + uppercase;
    }

    private EmbeddedUtil() {
        throw new AssertionError("EmbeddedUtil can not be instantiated");
    }

    static File getWorkingDirectory() {
        File parent = new File(System.getProperty("java.io.tmpdir"));
        // personalize the unpack folder to allow systems with many users using the same tmp folder to work
        File workDir = new File(parent, "embedded-pg-" + Objects.requireNonNullElse(System.getProperty("user.name"), "unknown"));

        ensureDirectory(workDir);

        return workDir;
    }

    static void ensureDirectory(@Nonnull File workDir) {

        long retryCount = 5;

        while (retryCount > 0) {
            if (workDir.exists()) {
                break;
            }
            if (workDir.mkdirs()) {
                break;
            }
            retryCount--;

            try {
                Thread.sleep(100 * (6 - retryCount));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Could not create working directory'" + workDir.getAbsolutePath() + "'", e);
            }

        }
        if (retryCount == 0) {
            throw new IllegalStateException("Could not create working directory '" + workDir.getAbsolutePath() + "' after 5 tries");
        }

        checkState(workDir.exists(), "'%s' does not exist!", workDir);
        checkState(workDir.isDirectory(), "'%s' exists but is not a directory!", workDir);

        if (!workDir.canWrite()) {
            checkState(workDir.setWritable(true, false), "Could not make directory '%s' writeable!", workDir);
        }

        checkState(workDir.canWrite(), "'%s' is a directory but can not be written!", workDir);
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

    static void rmdirs(File dir) throws IOException {
        if (dir.exists() && dir.isDirectory()) {
            try (Stream<Path> walk = Files.walk(dir.toPath())) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    static String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        long hours;
        long minutes;
        long secs;
        long ms;
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

    static String randomAlphaNumeric(int length) {
        return randomString(ALPHANUM, length);
    }

    static String randomLowercase(int length) {
        return randomString(LOWERCASE, length);
    }

    private static String sequence(char start, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append((char) (start + i));
        }
        return sb.toString();
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
