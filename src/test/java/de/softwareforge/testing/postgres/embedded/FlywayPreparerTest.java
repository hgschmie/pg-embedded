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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FlywayPreparerTest {

    @Test
    public void testWorking() throws Exception {
        try (DatabaseManager manager = DatabaseManager.multiDatabases()
                .withInstancePreparer(EmbeddedPostgres.Builder::withDefaults)
                .withDatabasePreparer(FlywayPreparer.forClasspathLocation("db/testing"))
                .build()
                .start()) {

            DatabaseInfo firstDatabaseInfo = manager.getDatabaseInfo();
            DatabaseInfo secondDatabaseInfo = manager.getDatabaseInfo();

            // different databases
            assertNotEquals(firstDatabaseInfo, secondDatabaseInfo);

            // both database contain the data
            assertEquals("bar", fetchData(firstDatabaseInfo));
            assertEquals("bar", fetchData(secondDatabaseInfo));
        }
    }

    @Test
    public void testMissing() throws Exception {
        try (DatabaseManager manager = DatabaseManager.singleDatabase()
                .withInstancePreparer(EmbeddedPostgres.Builder::withDefaults)
                .withDatabasePreparer(FlywayPreparer
                        .forClasspathLocation("db/non-existing")
                        .addCustomizer(c -> c.failOnMissingLocations(true)))
                .build()
                ) {

            IOException e = assertThrows(IOException.class, manager::start);

            assertTrue(e.getCause() instanceof FlywayException);
            assertTrue(e.getMessage().contains("Unable to resolve location classpath:db/non-existing"));
        }
    }


    @Test
    public void testBroken() throws Exception {
        try (DatabaseManager manager = DatabaseManager.singleDatabase()
                .withInstancePreparer(EmbeddedPostgres.Builder::withDefaults)
                .withDatabasePreparer(FlywayPreparer
                        .forClasspathLocation("db/broken")
                        .addCustomizer(c -> c.failOnMissingLocations(true)))
                .build()
        ) {

            IOException e = assertThrows(IOException.class, manager::start);

            assertTrue(e.getCause() instanceof FlywayException);
            // 42P01 - relation does not exist
            assertTrue(e.getMessage().contains("42P01"));
        }
    }


    private static String fetchData(DatabaseInfo databaseInfo) throws SQLException {
        try (Connection c = databaseInfo.asDataSource().getConnection();
                Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT * FROM foo")) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getString(1);
            }
        }
    }
}
