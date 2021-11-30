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

import de.softwareforge.testing.postgres.embedded.DatabaseInfo;
import de.softwareforge.testing.postgres.embedded.DatabaseManager;
import de.softwareforge.testing.postgres.embedded.DatabaseManager.DatabaseManagerBuilder;
import de.softwareforge.testing.postgres.embedded.EmbeddedPostgres;

import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="https://junit.org/junit5/docs/current/user-guide/#extensions">JUnit 5 extension</a> that manages an embedded PostgreSQL database server.
 * <p>
 * This extension can provide the {@link EmbeddedPostgres} instance, a {@link DatabaseInfo} or a {@link DataSource} object as test parameters.
 *
 * <ul>
 * <li>Using a {@link DatabaseInfo} parameter is equivalent to calling {@link #createDatabaseInfo()}.</li>
 * <li>Using a {@link DataSource} parameter is equivalent to calling {@link #createDataSource()}.</li>
 * </ul>
 */
public final class EmbeddedPgExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPgExtension.class);

    // multiple instances must use different namespaces
    private final Namespace PG_NAMESPACE = Namespace.create(UUID.randomUUID());

    private final DatabaseManager.Builder<DatabaseManager> databaseManagerBuilder;

    private volatile DatabaseManager databaseManager = null;

    private EmbeddedPgExtension(DatabaseManager.Builder<DatabaseManager> databaseManagerBuilder) {
        this.databaseManagerBuilder = databaseManagerBuilder;
    }

    /**
     * Creates a new {@link EmbeddedPgExtensionBuilder} that allows further customization of the {@link EmbeddedPgExtension}. The resulting extension manages
     * the database server in multi-mode (creating multiple databases).
     *
     * @return A {@link EmbeddedPgExtensionBuilder} instance. Never null.
     */
    @NonNull
    static EmbeddedPgExtensionBuilder multiDatabase() {
        return new EmbeddedPgExtensionBuilder(true);
    }

    /**
     * Creates a new {@link EmbeddedPgExtensionBuilder} that allows further customization of the {@link EmbeddedPgExtension}. The resulting extension manages
     * the database server in single-mode (using a single database instance).
     *
     * @return A {@link EmbeddedPgExtensionBuilder} instance. Never null.
     */
    @NonNull
    static EmbeddedPgExtensionBuilder singleDatabase() {
        return new EmbeddedPgExtensionBuilder(false);
    }

    /**
     * Default constructor which allows using this extension with the {@link org.junit.jupiter.api.extension.ExtendWith} annotation.
     * <p>
     * This is equivalent to using <pre>{@code
     *     @RegisterExtension
     *     public static EmbeddedPgExtension pg = MultiDatabaseBuilder.instanceWithDefaults().build();
     *     }</pre>
     */
    public EmbeddedPgExtension() {
        this(new DatabaseManagerBuilder(true).withInstancePreparer(EmbeddedPostgres.Builder::withDefaults));
    }

    /**
     * Returns a data source. Depending on the mode it returns a datasource connected to the same database (single mode) or a new database (multi mode) on every
     * call.
     *
     * @return A {@link DataSource} instance. This is never null.
     * @throws SQLException If a problem connecting to the database occurs.
     */
    @NonNull
    public DataSource createDataSource() throws SQLException {
        return createDatabaseInfo().asDataSource();
    }

    @VisibleForTesting
    EmbeddedPostgres getEmbeddedPostgres() {
        return databaseManager.getEmbeddedPostgres();
    }

    /**
     * Returns a new {@link DatabaseInfo} describing a database connection.
     * <p>
     * Depending on the mode, this either describes the same database (single mode) or a new database (multi mode).
     *
     * @return A {@link DatabaseInfo} instance. This is never null.
     * @throws SQLException If a problem connecting to the database occurs.
     */
    @NonNull
    public DatabaseInfo createDatabaseInfo() throws SQLException {
        checkState(databaseManager != null, "no before method has been called!");

        DatabaseInfo databaseInfo = databaseManager.getDatabaseInfo();
        LOG.info("Connection to {}", databaseInfo.asJdbcUrl());
        return databaseInfo;
    }

    @Override
    public void beforeAll(@NonNull ExtensionContext extensionContext) throws Exception {
        checkNotNull(extensionContext, "extensionContext is null");

        Store pgStore = extensionContext.getStore(PG_NAMESPACE);

        TestMode testMode = pgStore.getOrComputeIfAbsent(TestMode.TESTMODE_KEY,
                k -> new TestMode(extensionContext.getUniqueId(), databaseManagerBuilder.build()),
                TestMode.class);

        this.databaseManager = testMode.start(extensionContext.getUniqueId());
    }

    @Override
    public void afterAll(@NonNull ExtensionContext extensionContext) throws Exception {
        checkNotNull(extensionContext, "extensionContext is null");

        Store pgStore = extensionContext.getStore(PG_NAMESPACE);
        TestMode testMode = pgStore.get(TestMode.TESTMODE_KEY, TestMode.class);

        if (testMode != null) {
            this.databaseManager = testMode.stop(extensionContext.getUniqueId());
        }
    }

    @Override
    public void beforeEach(@NonNull ExtensionContext extensionContext) throws Exception {
        checkNotNull(extensionContext, "extensionContext is null");

        Store pgStore = extensionContext.getStore(PG_NAMESPACE);
        TestMode testMode = pgStore.getOrComputeIfAbsent(TestMode.TESTMODE_KEY,
                k -> new TestMode(extensionContext.getUniqueId(), databaseManagerBuilder.build()),
                TestMode.class);

        this.databaseManager = testMode.start(extensionContext.getUniqueId());
    }

    @Override
    public void afterEach(@NonNull ExtensionContext extensionContext) throws Exception {
        checkNotNull(extensionContext, "extensionContext is null");

        Store pgStore = extensionContext.getStore(PG_NAMESPACE);
        TestMode testMode = pgStore.get(TestMode.TESTMODE_KEY, TestMode.class);

        if (testMode != null) {
            this.databaseManager = testMode.stop(extensionContext.getUniqueId());
        }
    }

    @Override
    public boolean supportsParameter(@NonNull ParameterContext parameterContext, ExtensionContext extensionContext) {
        Type type = parameterContext.getParameter().getType();
        return type == EmbeddedPostgres.class
                || type == DatabaseInfo.class
                || type == DataSource.class;
    }

    @Override
    public Object resolveParameter(@NonNull ParameterContext parameterContext, ExtensionContext extensionContext) {
        Type type = parameterContext.getParameter().getType();
        try {
            if (type == EmbeddedPostgres.class) {
                return getEmbeddedPostgres();
            } else if (type == DatabaseInfo.class) {
                return createDatabaseInfo();

            } else if (type == DataSource.class) {
                return createDataSource();
            }
        } catch (SQLException e) {
            throw new ParameterResolutionException("Could not create " + type.getTypeName() + " instance", e);
        }
        return null;
    }

    /**
     * Builder for {@link EmbeddedPgExtension} customization.
     */
    public static final class EmbeddedPgExtensionBuilder extends DatabaseManager.Builder<EmbeddedPgExtension> {

        private EmbeddedPgExtensionBuilder(boolean multiMode) {
            super(multiMode);
        }

        /**
         * Create a {@link EmbeddedPgExtension} instance.
         *
         * @return A {@link EmbeddedPgExtension} instance. Is never null.
         */
        @Override
        @NonNull
        public EmbeddedPgExtension build() {
            DatabaseManager.Builder<DatabaseManager> databaseManagerBuilder = new DatabaseManagerBuilder(multiMode)
                    .withDatabasePreparers(databasePreparers.build())
                    .withInstancePreparers(instancePreparers.build());

            return new EmbeddedPgExtension(databaseManagerBuilder);
        }
    }

    private static final class TestMode {

        private static final Object TESTMODE_KEY = new Object();

        private final String id;
        private final DatabaseManager databaseManager;

        private TestMode(String id, DatabaseManager databaseManager) {
            this.id = id;
            this.databaseManager = databaseManager;
        }

        private DatabaseManager start(String id) throws Exception {
            if (this.id.equals(id)) {
                databaseManager.start();
            }

            return databaseManager;
        }

        private DatabaseManager stop(String id) throws Exception {
            if (this.id.equals(id)) {
                databaseManager.close();
                return null;
            }

            return databaseManager;
        }
    }
}
