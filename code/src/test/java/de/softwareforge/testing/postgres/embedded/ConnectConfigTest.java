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

import static com.google.common.base.Preconditions.checkState;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.softwareforge.testing.postgres.junit5.EmbeddedPgExtension;
import de.softwareforge.testing.postgres.junit5.SingleDatabaseBuilder;

import jakarta.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.postgresql.ds.common.BaseDataSource;

public class ConnectConfigTest {

    @Nested
    class WithoutParameters {

        @RegisterExtension
        public EmbeddedPgExtension db = SingleDatabaseBuilder.instanceWithDefaults()
                .build();

        @Test
        public void testWithoutParameters() throws SQLException {
            DatabaseInfo databaseInfo = db.createDatabaseInfo();

            Map<String, String> properties = databaseInfo.connectionProperties();
            assertEquals(0, properties.size());

            assertEquals("", databaseInfo.getAdditionalParameters());
        }
    }

    @Nested
    class WithParameters {

        private final CapturingDatabasePreparer preparer = new CapturingDatabasePreparer();

        @RegisterExtension
        public EmbeddedPgExtension db = SingleDatabaseBuilder.preparedInstanceWithDefaults(preparer)
                .withInstancePreparer(builder -> builder.addConnectionProperty("connectTimeout", "20").addConnectionProperty("logUnclosedConnections", "true"))
                .build();

        @Test
        public void testWithParameters() throws SQLException {
            DatabaseInfo databaseInfo = db.createDatabaseInfo();

            Map<String, String> properties = databaseInfo.connectionProperties();
            assertEquals(2, properties.size());
            assertEquals("20", properties.get("connectTimeout"));
            assertEquals("true", properties.get("logUnclosedConnections"));

            BaseDataSource testDatabase = (BaseDataSource) db.createDataSource();
            assertEquals("20", testDatabase.getProperty("connectTimeout"));
            assertEquals("true", properties.get("logUnclosedConnections"));

            BaseDataSource preparerDataSource = (BaseDataSource) preparer.getDataSource();
            assertEquals("20", preparerDataSource.getProperty("connectTimeout"));
            assertEquals("true", properties.get("logUnclosedConnections"));

            assertEquals("connectTimeout=20&logUnclosedConnections=true", databaseInfo.getAdditionalParameters());
        }
    }

    private static class CapturingDatabasePreparer implements EmbeddedPostgresPreparer<DataSource> {

        private DataSource dataSource;

        @Override
        public void prepare(@Nonnull DataSource dataSource) {
            checkState(this.dataSource == null, "database preparer has been called multiple times");
            this.dataSource = dataSource;
        }

        public DataSource getDataSource() {
            return dataSource;
        }
    }
}
