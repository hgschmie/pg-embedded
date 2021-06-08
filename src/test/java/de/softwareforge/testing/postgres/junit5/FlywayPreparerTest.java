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
package de.softwareforge.testing.postgres.junit5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.softwareforge.testing.postgres.embedded.DatabaseInfo;
import de.softwareforge.testing.postgres.embedded.FlywayPreparer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class FlywayPreparerTest {

    @RegisterExtension
    public static EmbeddedPgExtension singleDatabase = SingleDatabaseBuilder.preparedInstanceWithDefaults(FlywayPreparer.forClasspathLocation("db/testing"))
            .build();

    @RegisterExtension
    public static EmbeddedPgExtension multiDatabase = MultiDatabaseBuilder.preparedInstanceWithDefaults(FlywayPreparer.forClasspathLocation("db/testing"))
            .build();


    @Test
    public void testSingleTables() throws Exception {

        DatabaseInfo firstDatabaseInfo = singleDatabase.getConnectionInfo();
        DatabaseInfo secondDatabaseInfo = singleDatabase.getConnectionInfo();

        // get the same database on every call
        assertEquals(firstDatabaseInfo, secondDatabaseInfo);

        // make sure tables exist
        assertEquals("bar", fetchData(firstDatabaseInfo));
    }

    @Test
    public void testMultiTables() throws Exception {

        DatabaseInfo firstDatabaseInfo = multiDatabase.getConnectionInfo();
        DatabaseInfo secondDatabaseInfo = multiDatabase.getConnectionInfo();

        // different databases
        assertNotEquals(firstDatabaseInfo, secondDatabaseInfo);

        // both database contain the data
        assertEquals("bar", fetchData(firstDatabaseInfo));
        assertEquals("bar", fetchData(secondDatabaseInfo));
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
