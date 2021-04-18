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

import java.time.Duration;

import de.softwareforge.testing.postgres.embedded.DatabasePreparer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static de.softwareforge.testing.postgres.embedded.EmbeddedPostgres.DEFAULT_PG_STARTUP_WAIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class PreparedDbCustomizerTest {

    private static final DatabasePreparer EMPTY_PREPARER = ds -> {};

    @RegisterExtension
    public PreparedDbExtension dbA1 = EmbeddedPostgresExtension.preparedDatabase(EMPTY_PREPARER);
    @RegisterExtension
    public PreparedDbExtension dbA2 = EmbeddedPostgresExtension.preparedDatabase(EMPTY_PREPARER).customize(builder -> {});
    @RegisterExtension
    public PreparedDbExtension dbA3 = EmbeddedPostgresExtension.preparedDatabase(EMPTY_PREPARER)
            .customize(builder -> builder.setPGStartupWait(DEFAULT_PG_STARTUP_WAIT));
    @RegisterExtension
    public PreparedDbExtension dbB1 = EmbeddedPostgresExtension.preparedDatabase(EMPTY_PREPARER)
            .customize(builder -> builder.setPGStartupWait(Duration.ofSeconds(11)));
    @RegisterExtension
    public PreparedDbExtension dbB2 = EmbeddedPostgresExtension.preparedDatabase(EMPTY_PREPARER)
            .customize(builder -> builder.setPGStartupWait(Duration.ofSeconds(11)));

    @Test
    public void testCustomizers() {
        int dbA1Port = dbA1.getConnectionInfo().getPort();
        int dbA2Port = dbA2.getConnectionInfo().getPort();
        int dbA3Port = dbA3.getConnectionInfo().getPort();

        assertEquals(dbA1Port, dbA2Port);
        assertEquals(dbA1Port, dbA3Port);

        int dbB1Port = dbB1.getConnectionInfo().getPort();
        int dbB2Port = dbB2.getConnectionInfo().getPort();

        assertEquals(dbB1Port, dbB2Port);

        assertNotEquals(dbA1Port, dbB2Port);
    }
}
