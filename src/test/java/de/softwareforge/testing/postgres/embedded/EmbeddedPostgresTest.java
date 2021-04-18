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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class EmbeddedPostgresTest {

    @TempDir
    public Path tf;

    @Test
    public void testEmbeddedPg() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.defaultInstance();
                Connection c = pg.getPostgresDatabase().getConnection()) {
            Statement s = c.createStatement();
            ResultSet rs = s.executeQuery("SELECT 1");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
            assertFalse(rs.next());
        }
    }

    @Test
    public void testEmbeddedPgCreationWithNestedDataDirectory() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builderWithDefaults()
                .setDataDirectory(Files.createDirectories(tf.resolve("data-dir-parent").resolve("data-dir")))
                .build()) {
            // nothing to do
        }
    }

    @Test
    public void testValidLocaleSettingsPassthrough() throws IOException {

        EmbeddedPostgres.Builder builder = EmbeddedPostgres.builderWithDefaults();
        if (SystemUtils.IS_OS_WINDOWS) {
            builder.setLocaleConfig("locale", "de-de")
                    .setLocaleConfig("lc-messages", "de-de");
        } else if (SystemUtils.IS_OS_MAC) {
            builder.setLocaleConfig("locale", "de_DE")
                    .setLocaleConfig("lc-messages", "de_DE");
        } else if (SystemUtils.IS_OS_LINUX) {
            builder.setLocaleConfig("locale", "de_DE.utf8")
                    .setLocaleConfig("lc-messages", "de_DE.utf8");
        } else {
            fail("System not detected!");
        }

        try (EmbeddedPostgres pg = builder.build()) {
            // GNDN
        }
    }
}
