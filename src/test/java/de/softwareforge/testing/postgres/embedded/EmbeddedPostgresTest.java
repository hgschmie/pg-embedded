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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.postgresql.PGProperty.CONNECT_TIMEOUT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ds.PGSimpleDataSource;

public class EmbeddedPostgresTest {

    @TempDir
    public Path tempDir;

    @Test
    public void testEmbeddedPg() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.defaultInstance();
                Connection c = pg.createDefaultDataSource().getConnection();
                Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT 1")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertFalse(rs.next());
            }
        }
    }

    @Test
    public void testEmbeddedPgCreationWithNestedDataDirectory() throws Exception {
        Path dataPath = Files.createDirectories(tempDir.resolve("data-dir-parent").resolve("data-dir"));
        try (EmbeddedPostgres pg = EmbeddedPostgres.builderWithDefaults()
                .setDataDirectory(dataPath)
                .build()) {
            assertEquals(dataPath, pg.getDataDirectory().toPath());
        }
    }

    @Test
    public void testDatasources() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builderWithDefaults().addConnectionProperty(CONNECT_TIMEOUT.getName(), "20").build()) {
            DataSource ds1 = pg.createDefaultDataSource();

            DataSource ds2 = pg.createDefaultDatabaseInfo().asDataSource();

            assertSame(ds1.getClass(), ds2.getClass());

            PGSimpleDataSource pds1 = (PGSimpleDataSource) ds1;
            PGSimpleDataSource pds2 = (PGSimpleDataSource) ds2;

            assertArrayEquals(pds1.getServerNames(), pds2.getServerNames());
            assertArrayEquals(pds1.getPortNumbers(), pds2.getPortNumbers());
            assertEquals(pds1.getUser(), pds2.getUser());
            assertEquals(pds1.getConnectTimeout(), pds2.getConnectTimeout());
            assertEquals(20, pds1.getConnectTimeout());
        }

    }

    @Test
    public void testInitDbOptions() throws IOException {

        String locale = "";
        String lcMessages = "";
        EmbeddedPostgres.Builder builder = EmbeddedPostgres.builderWithDefaults();
        if (EmbeddedUtil.IS_OS_WINDOWS) {
            locale = "de-de";
            lcMessages = "de-de";
        } else if (EmbeddedUtil.IS_OS_MAC) {
            locale = "de_DE";
            lcMessages = "de_DE";
        } else if (EmbeddedUtil.IS_OS_LINUX) {
            locale = "de_DE.utf8";
            lcMessages = "de_DE.utf8";
        } else {
            fail("System not detected!");
        }
        builder.addInitDbConfiguration("locale", locale)
                .addInitDbConfiguration("lc-messages", lcMessages)
                .addInitDbConfiguration("no-sync", "");

        try (EmbeddedPostgres pg = builder.build()) {
            Map<String, String> localeConfig = pg.getLocaleConfiguration();
            assertEquals(locale, localeConfig.get("locale"));
            assertEquals(lcMessages, localeConfig.get("lc-messages"));
            assertEquals("", localeConfig.get("no-sync"));

            List<String> options = pg.createInitDbOptions();
            assertTrue(options.contains("--locale=" + locale));
            assertTrue(options.contains("--lc-messages=" + lcMessages));
            assertTrue(options.contains("--no-sync"));
        }
    }
}
