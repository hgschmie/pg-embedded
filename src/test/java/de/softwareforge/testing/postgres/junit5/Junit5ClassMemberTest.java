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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestMethodOrder(OrderAnnotation.class)
public class Junit5ClassMemberTest {

    @RegisterExtension
    public static EmbeddedPgExtension singleDatabase = SingleDatabaseBuilder.instanceWithDefaults().build();

    @RegisterExtension
    public static EmbeddedPgExtension multiDatabase = MultiDatabaseBuilder.instanceWithDefaults().build();

    @Test
    @Order(1)
    public void testTableCreation() throws Exception {
        // create tables in a single and a multi database
        // the static extension is the same database across all tests.
        createTable(singleDatabase, "table1");
        createTable(multiDatabase, "table2");
    }

    @Test
    @Order(2)
    public void testTableExists() throws Exception {
        // single database is shared between tests
        // multi database is not.
        assertTrue(existsTable(singleDatabase, "table1"));
        assertFalse(existsTable(multiDatabase, "table2"));
    }


    static int createTable(EmbeddedPgExtension extension, String table) throws SQLException {
        try (Connection connection = extension.createDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            return statement.executeUpdate(format("CREATE TABLE public.%s (a INTEGER)", table));
        }
    }

    static boolean existsTable(EmbeddedPgExtension extension, String table) throws SQLException {
        try (Connection connection = extension.createDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            String query = format("SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '%s')", table);
            try (ResultSet resultSet = statement.executeQuery(query)) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }
}
