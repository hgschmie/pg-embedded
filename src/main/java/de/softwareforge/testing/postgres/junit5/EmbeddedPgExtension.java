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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static de.softwareforge.testing.postgres.junit5.EmbeddedPgExtension.TestMode.TESTMODE_KEY;

import de.softwareforge.testing.postgres.embedded.DatabaseInfo;
import de.softwareforge.testing.postgres.embedded.DatabaseManager;
import de.softwareforge.testing.postgres.embedded.DatabaseManager.DatabaseManagerBuilder;
import de.softwareforge.testing.postgres.embedded.EmbeddedPostgres;

import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

import com.google.common.annotations.VisibleForTesting;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EmbeddedPgExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPgExtension.class);

    // multiple instances must use different namespaces
    private final Namespace PG_NAMESPACE = Namespace.create(UUID.randomUUID());

    private final DatabaseManager.Builder<DatabaseManager> databaseManagerBuilder;

    private volatile DatabaseManager databaseManager = null;

    private EmbeddedPgExtension(DatabaseManager.Builder<DatabaseManager> databaseManagerBuilder) {
        this.databaseManagerBuilder = databaseManagerBuilder;
    }

    public static EmbeddedPgExtensionBuilder multiDatabase() {
        return new EmbeddedPgExtensionBuilder(true);
    }

    public static EmbeddedPgExtensionBuilder singleDatabase() {
        return new EmbeddedPgExtensionBuilder(false);
    }

    /**
     * Returns the data source for the current instance.
     */
    public DataSource createDataSource() throws SQLException {
        return createDatabaseInfo().asDataSource();
    }

    @VisibleForTesting
    EmbeddedPostgres getEmbeddedPostgres() {
        return databaseManager.getEmbeddedPostgres();
    }

    /**
     * Returns a {@link DatabaseInfo} describing the database connection.
     */
    public DatabaseInfo createDatabaseInfo() throws SQLException {
        checkState(databaseManager != null, "no before method has been called!");

        DatabaseInfo databaseInfo = databaseManager.getDatabaseInfo();
        if (databaseInfo.exception().isEmpty()) {
            LOG.info("Connection to {}", databaseInfo.asJdbcUrl());
        }
        return databaseInfo;
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        checkNotNull(extensionContext, "extensionContext is null");

        Store pgStore = extensionContext.getStore(PG_NAMESPACE);

        TestMode testMode = pgStore.getOrComputeIfAbsent(TESTMODE_KEY,
                k -> new TestMode(extensionContext.getUniqueId(), databaseManagerBuilder.build()),
                TestMode.class);

        this.databaseManager = testMode.start(extensionContext.getUniqueId());
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        checkNotNull(extensionContext, "extensionContext is null");

        Store pgStore = extensionContext.getStore(PG_NAMESPACE);
        TestMode testMode = pgStore.get(TESTMODE_KEY, TestMode.class);

        if (testMode != null) {
            this.databaseManager = testMode.stop(extensionContext.getUniqueId());
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        checkNotNull(extensionContext, "extensionContext is null");

        Store pgStore = extensionContext.getStore(PG_NAMESPACE);
        TestMode testMode = pgStore.getOrComputeIfAbsent(TESTMODE_KEY,
                k -> new TestMode(extensionContext.getUniqueId(), databaseManagerBuilder.build()),
                TestMode.class);

        this.databaseManager = testMode.start(extensionContext.getUniqueId());
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        checkNotNull(extensionContext, "extensionContext is null");

        Store pgStore = extensionContext.getStore(PG_NAMESPACE);
        TestMode testMode = pgStore.get(TESTMODE_KEY, TestMode.class);

        if (testMode != null) {
            this.databaseManager = testMode.stop(extensionContext.getUniqueId());
        }
    }

    static class EmbeddedPgExtensionBuilder extends DatabaseManager.Builder<EmbeddedPgExtension> {

        private EmbeddedPgExtensionBuilder(boolean multiMode) {
            super(multiMode);
        }

        @Override
        public EmbeddedPgExtension build() {
            DatabaseManager.Builder<DatabaseManager> databaseManagerBuilder = new DatabaseManagerBuilder(multiMode)
                    .withDataSourcePreparer(dataSourcePreparer);

            instancePreparers.build().forEach(databaseManagerBuilder::withInstancePreparer);
            return new EmbeddedPgExtension(databaseManagerBuilder);
        }
    }

    static final class TestMode {

        static final Object TESTMODE_KEY = new Object();

        private final String id;
        private final DatabaseManager databaseManager;

        private TestMode(String id, DatabaseManager databaseManager) {
            this.id = id;
            this.databaseManager = databaseManager;
        }

        public DatabaseManager start(String id) throws Exception {
            if (this.id.equals(id)) {
                databaseManager.start();
            }

            return databaseManager;
        }

        public DatabaseManager stop(String id) throws Exception {
            if (this.id.equals(id)) {
                databaseManager.close();
                return null;
            }

            return databaseManager;
        }
    }
}
