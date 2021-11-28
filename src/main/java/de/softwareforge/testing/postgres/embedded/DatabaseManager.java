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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static de.softwareforge.testing.postgres.embedded.DatabaseInfo.PG_DEFAULT_USER;
import static java.lang.String.format;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.sql.DataSource;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controls database instances on a PostgreSQL server instances.
 */
public final class DatabaseManager implements AutoCloseable {

    private static final String PG_DEFAULT_ENCODING = "utf8";

    public static final Logger LOG = LoggerFactory.getLogger(DatabaseManager.class);

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();

    private final Set<EmbeddedPostgresPreparer<DataSource>> databasePreparers;
    private final Set<EmbeddedPostgresPreparer<EmbeddedPostgres.Builder>> instancePreparers;
    private final boolean multiMode;

    private volatile InstanceProvider instanceProvider = null;
    private volatile EmbeddedPostgres pg = null;

    private DatabaseManager(Set<EmbeddedPostgresPreparer<DataSource>> databasePreparers,
            Set<EmbeddedPostgresPreparer<EmbeddedPostgres.Builder>> instancePreparers,
            boolean multiMode) {
        this.databasePreparers = checkNotNull(databasePreparers, "databasePreparers is null");
        this.instancePreparers = checkNotNull(instancePreparers, "instancePreparers is null");
        this.multiMode = multiMode;
    }

    /**
     * Creates a new {@link Builder<DatabaseManager>} instance that will create a new database on each call to {@link #getDatabaseInfo()}.
     *
     * @return A builder instance.
     */
    @NonNull
    public static Builder<DatabaseManager> multiDatabases() {
        return new DatabaseManagerBuilder(true);
    }

    /**
     * Creates a new {@link Builder<DatabaseManager>} instance that will return a connection to the same database on each call to {@link #getDatabaseInfo()}.
     *
     * @return A builder instance.
     */
    @NonNull
    public static Builder<DatabaseManager> singleDatabase() {
        return new DatabaseManagerBuilder(false);
    }

    /**
     * Start the database server and the machinery that will provide new database instances.
     *
     * @return This object.
     * @throws IOException  The server could not be started.
     * @throws SQLException A SQL problem occured while trying to initialize the database.
     */
    @NonNull
    public DatabaseManager start() throws IOException, SQLException {
        if (!started.getAndSet(true)) {

            // bring up the embedded postgres server and call all instance preparer instances on it.
            EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder();

            for (EmbeddedPostgresPreparer<EmbeddedPostgres.Builder> instancePreparer : instancePreparers) {
                instancePreparer.prepare(builder);
            }

            this.pg = builder.build();

            final DataSource dataSource;

            if (multiMode) {
                // apply database setup to the template database.
                dataSource = pg.createTemplateDataSource();

                // the provider pipeline will create new instances based on the template database.
                this.instanceProvider = new InstanceProviderPipeline();
            } else {
                // apply database setup to the default database.
                dataSource = pg.createDefaultDataSource();

                // always return a reference to the default database.
                this.instanceProvider = () -> pg.createDefaultDatabaseInfo();
            }

            for (EmbeddedPostgresPreparer<DataSource> databasePreparer : databasePreparers) {
                databasePreparer.prepare(dataSource);
            }

            this.instanceProvider.start();
        }

        return this;
    }

    @Override
    public void close() throws Exception {
        checkState(started.get(), "not yet started!");
        if (!closed.getAndSet(true)) {
            instanceProvider.close();
            pg.close();
        }
    }

    /**
     * Returns a {@link DatabaseInfo} instance that describes a database. If this database provider is in multi mode, every call to this method will return a
     * new database instance. If it is in single mode, it will always return the same database instance.
     *
     * @return A {@link DatabaseInfo} instance. This is never null.
     * @throws SQLException Any error that happened during the database creation is thrown here.
     */
    @NonNull
    public DatabaseInfo getDatabaseInfo() throws SQLException {
        checkState(started.get(), "not yet started!");

        DatabaseInfo databaseInfo = instanceProvider.get();
        if (databaseInfo.exception().isPresent()) {
            throw databaseInfo.exception().get();
        }

        return databaseInfo;
    }

    /**
     * Return the {@link EmbeddedPostgres} instance that manages the database server which holds all of the databases managed by this database manager.
     *
     * @return An {@link EmbeddedPostgres} instance. Never null.
     */
    @NonNull
    public EmbeddedPostgres getEmbeddedPostgres() {
        checkState(started.get(), "not yet started!");
        return pg;
    }

    private interface InstanceProvider extends Supplier<DatabaseInfo>, AutoCloseable {

        default void start() {
        }

        @Override
        default void close() {
        }

        DatabaseInfo get();
    }

    private final class InstanceProviderPipeline implements InstanceProvider, Runnable {

        private final ExecutorService executor;
        private final SynchronousQueue<DatabaseInfo> nextDatabase = new SynchronousQueue<>();

        private final AtomicBoolean closed = new AtomicBoolean();

        InstanceProviderPipeline() {
            this.executor = Executors.newSingleThreadExecutor(
                    new ThreadFactoryBuilder()
                            .setDaemon(true)
                            .setNameFormat("instance-creator-" + pg.instanceId() + "-%d")
                            .build());

        }

        @Override
        public void start() {
            this.executor.submit(this);
        }

        @Override
        public void close() {
            if (!this.closed.getAndSet(true)) {
                executor.shutdownNow();
            }
        }

        @Override
        public void run() {
            while (!closed.get()) {
                try {
                    final String newDbName = EmbeddedUtil.randomLowercase(12);
                    try {
                        createDatabase(pg.createDefaultDataSource(), newDbName);
                        nextDatabase.put(DatabaseInfo.builder()
                                .dbName(newDbName)
                                .port(pg.getPort())
                                .connectionProperties(pg.getConnectionProperties())
                                .build());
                    } catch (SQLException e) {
                        // https://www.postgresql.org/docs/13/errcodes-appendix.html - 57P01 admin_shutdown
                        if (!e.getSQLState().equals("57P01")) {
                            LOG.warn("Caught SQL Exception (" + e.getSQLState() + "):", e);
                            nextDatabase.put(DatabaseInfo.forException(e));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Throwable t) {
                    LOG.warn("Caught Throwable in instance provider loop:", t);
                }
            }
        }

        @Override
        public DatabaseInfo get() {
            try {
                return nextDatabase.take();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    private static void createDatabase(final DataSource dataSource, final String databaseName) throws SQLException {
        try (Connection c = dataSource.getConnection();
                Statement stmt = c.createStatement()) {
            stmt.executeUpdate(format("CREATE DATABASE %s OWNER %s ENCODING = '%s'", databaseName, PG_DEFAULT_USER, PG_DEFAULT_ENCODING));
        }
    }

    /**
     * Builder template.
     *
     * @param <T> Object to create.
     */
    public abstract static class Builder<T> {

        protected ImmutableSet.Builder<EmbeddedPostgresPreparer<DataSource>> databasePreparers = ImmutableSet.builder();
        protected ImmutableSet.Builder<EmbeddedPostgresPreparer<EmbeddedPostgres.Builder>> instancePreparers = ImmutableSet.builder();
        protected final boolean multiMode;

        /**
         * Creates a new builder.
         *
         * @param multiMode True if the resulting object should be in multi mode (create multiple database instances) or single mode (use only one instance).
         */
        protected Builder(boolean multiMode) {
            this.multiMode = multiMode;
        }

        /**
         * @deprecated Use {@link #withDatabasePreparer(EmbeddedPostgresPreparer)}.
         */
        @Deprecated
        @NonNull
        public Builder<T> withPreparer(@NonNull DatabasePreparer databasePreparer) {
            checkNotNull(databasePreparer, "databasePreparer is null");
            return withDatabasePreparer(databasePreparer::prepare);
        }

        /**
         * Add a preparer for the template database. Each preparer is called once when the database manager starts to prepare the template database. This can be
         * used to create tables, sequences etc. or preload the databases with information. In multi database mode, the template database is used and each
         * created database will have this information cloned.
         *
         * @param databasePreparer A {@link EmbeddedPostgresPreparer<DataSource>} instance. Must not be null.
         * @return This object instance.
         */
        @NonNull
        public Builder<T> withDatabasePreparer(@NonNull EmbeddedPostgresPreparer<DataSource> databasePreparer) {
            this.databasePreparers.add(checkNotNull(databasePreparer, "databasePreparer is null"));
            return this;
        }

        /**
         * Add preparers for the template database. Each preparer is called once when the database manager starts to prepare the template database. This can be
         * used to create tables, sequences etc. or preload the databases with information. In multi database mode, the template database is used and each
         * created database will have this information cloned.
         *
         * @param databasePreparers A set of {@link EmbeddedPostgresPreparer<DataSource>} instances. Must not be null.
         * @return This object instance.
         */
        @NonNull
        public Builder<T> withDatabasePreparers(@NonNull Set<EmbeddedPostgresPreparer<DataSource>> databasePreparers) {
            this.databasePreparers.addAll(checkNotNull(databasePreparers, "databasePreparers is null"));
            return this;
        }

        /**
         * Add a preparer for the {@link EmbeddedPostgres.Builder} object. Each preparer is called once when the {@link EmbeddedPostgres} instance that manages
         * the server is created.
         *
         * @param instancePreparer A {@link EmbeddedPostgresPreparer<EmbeddedPostgres.Builder>} instance. Must not be null.
         * @return This object instance.
         */
        @NonNull
        public Builder<T> withInstancePreparer(@NonNull EmbeddedPostgresPreparer<EmbeddedPostgres.Builder> instancePreparer) {
            this.instancePreparers.add(checkNotNull(instancePreparer, "instancePreparer is null"));
            return this;
        }

        /**
         * Add preparers for the {@link EmbeddedPostgres.Builder} object. Each preparer is called once when the {@link EmbeddedPostgres} instance that manages
         * the server is created.
         *
         * @param instancePreparers A set of {@link EmbeddedPostgresPreparer<EmbeddedPostgres.Builder>} instances. Must not be null.
         * @return This object instance.
         */
        @NonNull
        public Builder<T> withInstancePreparers(@NonNull Set<EmbeddedPostgresPreparer<EmbeddedPostgres.Builder>> instancePreparers) {
            this.instancePreparers.addAll(checkNotNull(instancePreparers, "instancePreparers is null"));
            return this;
        }

        /**
         * @deprecated Use {@link #withInstancePreparer(EmbeddedPostgresPreparer)}.
         */
        @Deprecated
        @NonNull
        public Builder<T> withCustomizer(@NonNull EmbeddedPostgres.BuilderCustomizer customizer) {
            checkNotNull(customizer, "customizer is null");
            this.instancePreparers.add(customizer::customize);
            return this;
        }

        /**
         * Creates a new instance.
         *
         * @return The instance to create.
         */
        @NonNull
        public abstract T build();
    }

    /**
     * Create new {@link DatabaseManager} instances.
     */
    public static final class DatabaseManagerBuilder extends Builder<DatabaseManager> {

        /**
         * Creates a new builder for {@link DatabaseManager} instances.
         *
         * @param multiMode True if the database manager should return a new database instance for every {@link DatabaseManager#getDatabaseInfo()}} call, false
         *                  if it should return the same database instance.
         */
        public DatabaseManagerBuilder(boolean multiMode) {
            super(multiMode);
        }

        /**
         * Creates a new {@link DatabaseManager} instance from the builder.
         *
         * @return A database manager. Never null.
         */
        @Override
        @NonNull
        public DatabaseManager build() {
            return new DatabaseManager(databasePreparers.build(), instancePreparers.build(), multiMode);
        }
    }
}
