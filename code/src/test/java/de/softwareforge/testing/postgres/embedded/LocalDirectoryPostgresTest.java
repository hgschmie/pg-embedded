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

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class LocalDirectoryPostgresTest {

    // pg-embedded.test.local-dir is set in pom.xml
    private static final File LOCAL_INSTALL_LOCATION = new File(System.getProperty("pg-embedded.test.local-dir", "/usr/local"));
    private static final File LOCAL_INSTALL_BIN_POSTGRES = new File(LOCAL_INSTALL_LOCATION, "/bin/postgres");

    @Test
    public void testEmbeddedPg() throws Exception {
        Assumptions.assumeTrue(LOCAL_INSTALL_BIN_POSTGRES.exists(), format("Skipping test, PostgreSQL binary not found at %s", LOCAL_INSTALL_BIN_POSTGRES));

        try (EmbeddedPostgres pg = EmbeddedPostgres.builderWithDefaults()
                // pg-embedded.test.unix-socket-dir is set in pom.xml. Can not use "just defaults" because some Linux distributions try to be smart and
                // use /var/run/postgresql as default which is only writable by the PostgresQL unix user. So this test would crash with "Permission denied".
                .addServerConfiguration(System.getProperty("pg-embedded.test.unix-socket-dir", "unix_socket_directories"), System.getProperty("java.io.tmpdir"))
                .useLocalPostgresInstallation(LOCAL_INSTALL_LOCATION)
                .build();
                Connection c = pg.createDefaultDataSource().getConnection();
                Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertFalse(rs.next());
            }
        }
    }
}
