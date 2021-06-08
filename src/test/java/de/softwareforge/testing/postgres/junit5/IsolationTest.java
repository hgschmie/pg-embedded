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

import static de.softwareforge.testing.postgres.junit5.Junit5ClassMemberTest.createTable;
import static de.softwareforge.testing.postgres.junit5.Junit5ClassMemberTest.existsTable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class IsolationTest {

    @RegisterExtension
    public EmbeddedPgExtension pg1 = SingleSchemaBuilder.instanceWithDefaults().build();

    @RegisterExtension
    public EmbeddedPgExtension pg2 = SingleSchemaBuilder.instanceWithDefaults().build();

    @Test
    public void testDoubleTable() throws Exception {
        assertEquals(0, createTable(pg1, "table1"));
        SQLException e = assertThrows(SQLException.class, () -> createTable(pg1, "table1"));
        // https://www.postgresql.org/docs/8.2/errcodes-appendix.html 42P07 - DUPLICATE TABLE
        assertEquals("42P07", e.getSQLState());
        assertTrue(existsTable(pg1, "table1"));
    }


    @Test
    public void testSameTable() throws Exception {
        assertEquals(0, createTable(pg1, "table1"));
        assertEquals(0, createTable(pg2, "table1"));

        assertTrue(existsTable(pg1, "table1"));
        assertTrue(existsTable(pg2, "table1"));
    }

    @Test
    public void testDifferentTable() throws Exception {
        assertEquals(0, createTable(pg1, "table1"));
        assertEquals(0, createTable(pg2, "table2"));

        assertTrue(existsTable(pg1, "table1"));
        assertFalse(existsTable(pg1, "table2"));
        assertTrue(existsTable(pg2, "table2"));
        assertFalse(existsTable(pg2, "table1"));
    }
}
