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

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestMethodOrder(OrderAnnotation.class)
public class Junit5InstanceMemberTest {

    @RegisterExtension
    public EmbeddedPgExtension singleDatabase = SingleDatabaseBuilder.instanceWithDefaults().build();

    @RegisterExtension
    public EmbeddedPgExtension multiDatabase = MultiDatabaseBuilder.instanceWithDefaults().build();

    @Test
    @Order(1)
    public void testTableCreation() throws Exception {
        // create tables in a single and a multi database
        // the extension creates a new database for each test
        createTable(singleDatabase, "table1");
        createTable(multiDatabase, "table2");
    }

    @Test
    @Order(2)
    public void testTableExists() throws Exception {
        // neither database is shared across tests
        assertFalse(existsTable(singleDatabase, "table1"));
        assertFalse(existsTable(multiDatabase, "table2"));
    }
}
