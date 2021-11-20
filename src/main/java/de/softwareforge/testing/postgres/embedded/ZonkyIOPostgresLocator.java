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
import static java.lang.String.format;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves pre-bundled PostgreSQL archives from the classpath. This matches the pre-bundled versions of PostgreSQL from
 * https://github.com/zonkyio/embedded-postgres-binaries.
 */
public final class ZonkyIOPostgresLocator implements Supplier<InputStream> {

    public static final Logger LOG = LoggerFactory.getLogger(ZonkyIOPostgresLocator.class);

    private static final boolean PREFER_NATIVE = Boolean.getBoolean("pg-embedded.prefer-native");

    private final String architecture;
    private final String os;

    public ZonkyIOPostgresLocator(@Nonnull String os, @Nonnull String architecture) {
        this.os = checkNotNull(os, "os is null");
        this.architecture = checkNotNull(architecture, "architecture is null");
        LOG.debug(format("Detected a %s %s system", architecture, os));
    }

    ZonkyIOPostgresLocator() {
        this(computeOS(), computeArchitecture());
    }

    @Override
    public InputStream get() {
        return ZonkyIOPostgresLocator.class.getResourceAsStream(format("/postgres-%s-%s.txz", computeOS(), computeArchitecture()));
    }

    @Override
    public String toString() {
        return format("ZonkyIO Stream locator for PostgreSQL (arch: %s os: %s)", architecture, os);
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
        return architecture.equals(that.architecture) && os.equals(that.os);
    }

    @Override
    public int hashCode() {
        return Objects.hash(architecture, os);
    }

    private static String computeArchitecture() {
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
