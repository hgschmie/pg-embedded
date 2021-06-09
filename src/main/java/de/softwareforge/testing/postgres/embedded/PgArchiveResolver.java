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

import java.io.IOException;
import java.io.InputStream;

/**
 * A strategy for resolving PostgreSQL binaries.
 *
 * @see ZonkyIOPostgresArchiveResolver
 */
public interface PgArchiveResolver {

    /**
     * Returns an input stream with the postgres archive for the given systen and hardware architecture.
     *
     * @param system          a system identification (Darwin, Linux...)
     * @param machineHardware a machine hardware architecture (x86_64...)
     * @return the binary
     * @throws IOException if no archive could be found.
     */
    InputStream locatePgArchive(String system, String machineHardware) throws IOException;
}