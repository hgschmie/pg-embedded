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

import de.softwareforge.testing.postgres.embedded.FlywayPreparer;
import de.softwareforge.testing.postgres.embedded.SchemaInfo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class FlywayPreparerTest {

    @RegisterExtension
    public static EmbeddedPgExtension singleDatabase = SingleSchemaBuilder.preparedInstanceWithDefaults(FlywayPreparer.forClasspathLocation("db/testing"))
            .build();

    @RegisterExtension
    public static EmbeddedPgExtension multiDatabase = MultiSchemaBuilder.preparedInstanceWithDefaults(FlywayPreparer.forClasspathLocation("db/testing"))
            .build();


    @Test
    public void testSingleTables() throws Exception {

        SchemaInfo firstSchemaInfo = singleDatabase.getConnectionInfo();
        SchemaInfo secondSchemaInfo = singleDatabase.getConnectionInfo();

        // get the same database on every call
        assertEquals(firstSchemaInfo, secondSchemaInfo);

        // make sure tables exist
        assertEquals("bar", fetchData(firstSchemaInfo));
    }

    @Test
    public void testMultiTables() throws Exception {

        SchemaInfo firstSchemaInfo = multiDatabase.getConnectionInfo();
        SchemaInfo secondSchemaInfo = multiDatabase.getConnectionInfo();

        // different databases
        assertNotEquals(firstSchemaInfo, secondSchemaInfo);

        // both database contain the data
        assertEquals("bar", fetchData(firstSchemaInfo));
        assertEquals("bar", fetchData(secondSchemaInfo));
    }

    private static String fetchData(SchemaInfo schemaInfo) throws SQLException {
        try (Connection c = schemaInfo.asDataSource().getConnection();
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
