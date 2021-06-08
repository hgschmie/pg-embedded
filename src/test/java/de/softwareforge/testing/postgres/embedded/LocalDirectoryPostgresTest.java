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

    private static final File USR_LOCAL = new File("/usr/local");
    private static final File USR_LOCAL_BIN_POSTGRES = new File(USR_LOCAL, "/bin/postgres");

    @Test
    public void testEmbeddedPg() throws Exception {
        Assumptions.assumeTrue(USR_LOCAL_BIN_POSTGRES.exists(), "Skipping test, PostgreSQL binary not found in /usr/local/bin");

        try (EmbeddedPostgres pg = EmbeddedPostgres.builderWithDefaults().setPostgresBinaryDirectory(USR_LOCAL).build();
                Connection c = pg.getDatabase().getConnection();
                Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertFalse(rs.next());
            }
        }
    }
}
