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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class PreparedDbProviderTest {

    @Test
    public void testSingleDatabase() throws Exception {
        try (SchemaManager provider = SchemaManager.singleSchema()
                .withCustomizer(EmbeddedPostgres.Builder::withDefaults)
                .build()
                .start()) {

            // every call to getConnectionInfo returns the same schema
            SchemaInfo firstSchemaInfo = provider.getConnectionInfo();
            SchemaInfo secondSchemaInfo = provider.getConnectionInfo();

            assertEquals(firstSchemaInfo, secondSchemaInfo);
        }
    }

    @Test
    public void testMultiDatabase() throws Exception {
        try (SchemaManager provider = SchemaManager.multiSchema()
                .withCustomizer(EmbeddedPostgres.Builder::withDefaults)
                .build()
                .start()) {

            // every call to getConnectionInfo returns a new schema
            SchemaInfo firstSchemaInfo = provider.getConnectionInfo();
            SchemaInfo secondSchemaInfo = provider.getConnectionInfo();

            assertNotEquals(firstSchemaInfo, secondSchemaInfo);
        }
    }
}
