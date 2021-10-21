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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import org.junit.jupiter.api.ClassOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestClassOrder(OrderAnnotation.class)
public class Junit5NestedTest {

    @RegisterExtension
    public static EmbeddedPgExtension singleDatabase = SingleDatabaseBuilder.instanceWithDefaults().build();

    @RegisterExtension
    public static EmbeddedPgExtension multiDatabase = MultiDatabaseBuilder.instanceWithDefaults().build();

    @Nested
    @Order(1)
    @TestInstance(PER_CLASS)
    class TableCreation {

        @Test
        public void testTableCreation() throws Exception {
            // create tables in a single and a multi database
            // the static extension is the same database across all tests.
            createTable(singleDatabase, "table1");
            createTable(multiDatabase, "table2");
        }
    }

    @Nested
    @Order(2)
    @TestInstance(PER_CLASS)
    class TableValidation {

        @Test
        public void testTableExists() throws Exception {
            // single database is shared between tests
            // multi database is not.
            assertTrue(existsTable(singleDatabase, "table1"));
            assertFalse(existsTable(multiDatabase, "table2"));
        }
    }
}
