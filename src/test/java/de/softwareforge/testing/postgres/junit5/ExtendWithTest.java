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

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.softwareforge.testing.postgres.embedded.DatabaseInfo;
import de.softwareforge.testing.postgres.embedded.EmbeddedPostgres;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EmbeddedPgExtension.class)
public class ExtendWithTest {

    @Test
    public void testEmbeddedPostgres(EmbeddedPostgres pg) throws SQLException {
        assertNotNull(pg);
        testDataSource(pg.createDefaultDataSource());
    }

    @Test
    public void testDatabaseInfo(DatabaseInfo databaseInfo) throws SQLException {
        assertNotNull(databaseInfo);

        testDataSource(databaseInfo.asDataSource());
    }

    @Test
    public void testDataSource(DataSource dataSource) throws SQLException {
        assertFalse(existsTable(dataSource, "table1"));
        assertFalse(existsTable(dataSource, "table2"));

        assertEquals(0, createTable(dataSource, "table1"));
        assertEquals(0, createTable(dataSource, "table2"));

        assertTrue(existsTable(dataSource, "table1"));
        assertTrue(existsTable(dataSource, "table2"));
    }

    static int createTable(DataSource ds, String table) throws SQLException {
        try (Connection connection = ds.getConnection();
                Statement statement = connection.createStatement()) {
            return statement.executeUpdate(format("CREATE TABLE public.%s (a INTEGER)", table));
        }
    }

    static boolean existsTable(DataSource ds, String table) throws SQLException {
        try (Connection connection = ds.getConnection();
                Statement statement = connection.createStatement()) {
            String query = format("SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '%s')", table);
            try (ResultSet resultSet = statement.executeQuery(query)) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }
}
