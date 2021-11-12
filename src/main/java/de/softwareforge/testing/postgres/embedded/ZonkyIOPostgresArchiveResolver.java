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

/**
 * Resolves pre-bundled archives from the classpath. This matches the pre-bundled versions of PostgreSQL from https://github.com/zonkyio/embedded-postgres-binaries
 */
enum ZonkyIOPostgresArchiveResolver implements PgArchiveResolver {

    INSTANCE;

    @Override
    public InputStream locatePgArchive(String system, String machineHardware) {
        checkNotNull(system, "system is null");
        checkNotNull(machineHardware, "machineHardware is null");

        return EmbeddedPostgres.class.getResourceAsStream(format("/postgres-%s-%s.txz", system, machineHardware));
    }
}
