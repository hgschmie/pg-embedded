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

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.google.common.base.Suppliers;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves PostgreSQL archives from the Maven repository. Looks for the zonky.io artifacts located at
 * <code>io.zonky.test.postgres:embedded-postgres-binaries-&lt;os&gt;-&lt;arch&gt;</code>.
 * <p>
 * See <a href="https://github.com/zonkyio/embedded-postgres-binaries">The Zonky IO github page</a> for more details.
 */
public final class ZonkyIOPostgresLocator implements NativeBinaryLocator {

    private static final String ZONKY_GROUP_ID = "io.zonky.test.postgres";
    private static final String ZONKY_ARTIFACT_ID_TEMPLATE = "embedded-postgres-binaries-%s-%s";

    public static final Logger LOG = LoggerFactory.getLogger(ZonkyIOPostgresLocator.class);

    private static final boolean PREFER_NATIVE = Boolean.getBoolean("pg-embedded.prefer-native");

    private final String architecture;
    private final String os;
    private final String serverVersion;

    private final MavenArtifactLoader artifactLoader = new MavenArtifactLoader();

    private final Supplier<File> fileSupplier = Suppliers.memoize(this::loadArtifact);

    ZonkyIOPostgresLocator(String serverVersion) {
        this.serverVersion = checkNotNull(serverVersion, "serverVersion is null");

        this.os = computeOS();
        this.architecture = computeTarXzArchitectureName();
        LOG.debug(format("Detected a %s %s system, using PostgreSQL version %s", architecture, os, serverVersion));
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try {
            File artifactFile = fileSupplier.get();
            return createJarStream(artifactFile);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public String getIdentifier() throws IOException {
        // the optimized identifier computation saves ~ 1% CPU according to the profiler
        try {
            File artifactFile = fileSupplier.get();
            HashCode hashCode = Hashing.murmur3_128().hashString(artifactFile.getAbsolutePath(), StandardCharsets.UTF_8);
            return INSTALL_DIRECTORY_PREFIX + BaseEncoding.base16().encode(hashCode.asBytes());
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private File loadArtifact() {
        try {
            String artifactId = format(ZONKY_ARTIFACT_ID_TEMPLATE, this.os, computeJarArchitectureName());

            // alpine hack
            if (EmbeddedUtil.IS_ALPINE_LINUX) {
                artifactId += "-alpine";
            }

            String version = artifactLoader.findLatestVersion(ZONKY_GROUP_ID, artifactId, serverVersion);
            File file = artifactLoader.getArtifactFile(ZONKY_GROUP_ID, artifactId, version);
            checkState(file != null && file.exists(), "Could not locate artifact file for %s:%s", artifactId, version);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private InputStream createJarStream(File file) {
        try {
            JarFile jar = new JarFile(file);
            String entryName = format("postgres-%s-%s", computeOS(), computeTarXzArchitectureName());

            // alpine hack
            if (EmbeddedUtil.IS_ALPINE_LINUX) {
                entryName += "-alpine_linux";
            }

            JarEntry jarEntry = jar.getJarEntry(entryName + ".txz");
            checkState(jarEntry != null, "Could not locate %s in the jar file (%s)", entryName, file.getAbsoluteFile());

            // When the input stream gets closed, close the jar file as well.
            return new FilterInputStream(jar.getInputStream(jarEntry)) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        jar.close();
                    }
                }
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return format("ZonkyIO Stream locator for PostgreSQL (arch: %s os: %s, version: %s)", architecture, os, serverVersion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ZonkyIOPostgresLocator that = (ZonkyIOPostgresLocator) o;
        return architecture.equals(that.architecture) && os.equals(that.os) && serverVersion.equals(that.serverVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(architecture, os, serverVersion);
    }

    private static String computeTarXzArchitectureName() {
        String architecture = EmbeddedUtil.OS_ARCH;
        if (EmbeddedUtil.IS_ARCH_X86_64) {
            architecture = "x86_64";  // Zonky uses x86_64
        } else if (EmbeddedUtil.IS_ARCH_AARCH64) {
            if (!PREFER_NATIVE && EmbeddedUtil.IS_OS_MAC) {
                // Mac binaries are fat binaries stored as x86_64
                architecture = "x86_64";
            } else {
                architecture = "arm_64";
            }
        } else if (EmbeddedUtil.IS_ARCH_AARCH32) {
            architecture = "arm_32";
        }
        return architecture;
    }

    private static String computeJarArchitectureName() {
        String architecture = EmbeddedUtil.OS_ARCH;
        if (EmbeddedUtil.IS_ARCH_X86_64) {
            architecture = "amd64";  // Zonky uses amd64 for the jar name
        } else if (EmbeddedUtil.IS_ARCH_AARCH64) {
            if (!PREFER_NATIVE && EmbeddedUtil.IS_OS_MAC) {
                // Mac binaries are fat binaries stored as amd64
                architecture = "amd64";
            } else {
                architecture = "arm64v8";
            }
        } else if (EmbeddedUtil.IS_ARCH_AARCH32) {
            architecture = "arm32v7";
        }
        return architecture;
    }

    private static String computeOS() {
        String os = EmbeddedUtil.OS_NAME;
        if (EmbeddedUtil.IS_OS_LINUX) {
            os = "linux";
        } else if (EmbeddedUtil.IS_OS_MAC) {
            os = "darwin";
        } else if (EmbeddedUtil.IS_OS_WINDOWS) {
            os = "windows";
        }
        return os;
    }
}
