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

import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Requires a compatible postgres binary on the classpath (as part of the dependencies in test
 * scope in the project pom.
 */

public class ClasspathLocatorTest {

    @Test
    public void testClasspathLocator() throws Exception {
        String name = System.getProperty("pg-embedded.test.binary-name");
        Assumptions.assumeTrue(name != null && !name.isEmpty(), "No binary name set, skipping test");

        try (EmbeddedPostgres pg = EmbeddedPostgres.builderWithDefaults()
                .setNativeBinaryManager(new TarXzCompressedBinaryManager(new ClasspathLocator(name)))
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

    static class ClasspathLocator implements NativeBinaryLocator {
        private final String name;

        ClasspathLocator(String name) {
            this.name = name;
        }

        @Override
        public InputStream getInputStream() {
            return EmbeddedPostgres.class.getResourceAsStream("/postgres-" + name + ".txz");
        }
    }
}
