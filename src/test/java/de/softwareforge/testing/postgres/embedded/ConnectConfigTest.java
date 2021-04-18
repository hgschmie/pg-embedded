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

import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import de.softwareforge.testing.postgres.junit5.EmbeddedPostgresExtension;
import de.softwareforge.testing.postgres.junit5.PreparedDbExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.postgresql.ds.common.BaseDataSource;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConnectConfigTest {

    private final CapturingDatabasePreparer preparer = new CapturingDatabasePreparer();

    @RegisterExtension
    public PreparedDbExtension db = EmbeddedPostgresExtension.preparedDatabase(preparer)
            .customize(builder -> builder.setConnectConfig("connectTimeout", "20"));

    @Test
    public void test() throws SQLException {
        ConnectionInfo connectionInfo = db.getConnectionInfo();

        Map<String, String> properties = connectionInfo.getProperties();
        assertEquals(1, properties.size());
        assertEquals("20", properties.get("connectTimeout"));

        BaseDataSource testDatabase = (BaseDataSource) db.getTestDatabase();
        assertEquals("20", testDatabase.getProperty("connectTimeout"));

        BaseDataSource preparerDataSource = (BaseDataSource) preparer.getDataSource();
        assertEquals("20", preparerDataSource.getProperty("connectTimeout"));
    }

    private class CapturingDatabasePreparer implements DatabasePreparer {

        private DataSource dataSource;

        @Override
        public void prepare(DataSource dataSource) {
            checkState(this.dataSource == null, "database preparer has been called multiple times");
            this.dataSource = dataSource;
        }

        public DataSource getDataSource() {
            return dataSource;
        }
    }
}
