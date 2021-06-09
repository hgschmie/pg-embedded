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

public class DatabaseInfoTest {

    @Test
    public void testMinimal() {
        DatabaseInfo databaseInfo = DatabaseInfo.builder().port(12345).build();
        assertEquals(DatabaseInfo.PG_DEFAULT_USER, databaseInfo.user());
        assertEquals(DatabaseInfo.PG_DEFAULT_DB, databaseInfo.dbName());
        assertEquals(12345, databaseInfo.port());
        assertTrue(databaseInfo.properties().isEmpty());
        assertTrue(databaseInfo.exception().isEmpty());
    }

    @Test
    public void testFull() {
        String dbName = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ROOT);
        String user = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ROOT);
        int port = ThreadLocalRandom.current().nextInt(65536);

        String propertyName = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ROOT);
        String propertyValue = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ROOT);

        DatabaseInfo databaseInfo = DatabaseInfo.builder()
                .dbName(dbName)
                .user(user)
                .port(port)
                .addProperty(propertyName, propertyValue)
                .build();

        assertEquals(user, databaseInfo.user());
        assertEquals(dbName, databaseInfo.dbName());
        assertEquals(port, databaseInfo.port());
        assertEquals(1, databaseInfo.properties().size());
        assertEquals(propertyValue, databaseInfo.properties().get(propertyName));
        assertTrue(databaseInfo.exception().isEmpty());
    }

    @Test
    public void testException() {
        DatabaseInfo databaseInfo = DatabaseInfo.forException(new SQLException());
        assertTrue(databaseInfo.exception().isPresent());
        assertEquals(SQLException.class, databaseInfo.exception().get().getClass());
    }

}