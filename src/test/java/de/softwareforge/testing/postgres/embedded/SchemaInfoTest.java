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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

public class SchemaInfoTest {

    @Test
    public void testMinimal() {
        SchemaInfo schemaInfo = SchemaInfo.builder().port(12345).build();
        assertEquals(SchemaInfo.PG_DEFAULT_USER, schemaInfo.user());
        assertEquals(SchemaInfo.PG_DEFAULT_DB, schemaInfo.dbName());
        assertEquals(12345, schemaInfo.port());
        assertTrue(schemaInfo.properties().isEmpty());
        assertTrue(schemaInfo.exception().isEmpty());
    }

    @Test
    public void testFull() {
        String dbName = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ROOT);
        String user = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ROOT);
        int port = ThreadLocalRandom.current().nextInt(65536);

        String propertyName = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ROOT);
        String propertyValue = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ROOT);

        SchemaInfo schemaInfo = SchemaInfo.builder()
                .dbName(dbName)
                .user(user)
                .port(port)
                .addProperty(propertyName, propertyValue)
                .build();

        assertEquals(user, schemaInfo.user());
        assertEquals(dbName, schemaInfo.dbName());
        assertEquals(port, schemaInfo.port());
        assertEquals(1, schemaInfo.properties().size());
        assertEquals(propertyValue, schemaInfo.properties().get(propertyName));
        assertTrue(schemaInfo.exception().isEmpty());
    }

    @Test
    public void testException() {
        SchemaInfo schemaInfo = SchemaInfo.forException(new SQLException());
        assertTrue(schemaInfo.exception().isPresent());
        assertEquals(SQLException.class, schemaInfo.exception().get().getClass());
    }

}
