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

import de.softwareforge.testing.postgres.embedded.EmbeddedPostgres.BuilderCustomizer;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.sql.DataSource;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the various databases within a postgres instance.
 */
public class DatabaseManager implements AutoCloseable {

    public static final Logger LOG = LoggerFactory.getLogger(DatabaseManager.class);

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();

    private final DatabasePreparer databasePreparer;
    private final Set<EmbeddedPostgres.BuilderCustomizer> customizers;
    private final boolean multiMode;

    private volatile InstanceProvider instanceProvider = null;
    private volatile EmbeddedPostgres pg = null;

    private DatabaseManager(DatabasePreparer databasePreparer,
            Set<EmbeddedPostgres.BuilderCustomizer> customizers,
            boolean multiMode) {
        this.databasePreparer = checkNotNull(databasePreparer, "databasePreparer is null");
        this.customizers = checkNotNull(customizers, "customizers is null");
        this.multiMode = multiMode;
    }

    public static Builder<DatabaseManager> multiDatabases() {
        return new DatabaseManagerBuilder(true);
    }

    public static Builder<DatabaseManager> singleDatabase() {
        return new DatabaseManagerBuilder(false);
    }

    public DatabaseManager start() throws IOException, SQLException {
        if (!started.getAndSet(true)) {
            EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder();
            for (BuilderCustomizer customizer : customizers) {
                customizer.customize(builder);
            }

            this.pg = builder.build();

            DataSource dataSourceToPrepare = multiMode ? pg.createTemplateDataSource() : pg.createDefaultDataSource();
            databasePreparer.prepare(dataSourceToPrepare);

            this.instanceProvider = multiMode ? new InstanceProviderPipeline() : () -> pg.createDefaultDatabaseInfo();

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

    public DatabaseInfo getDatabaseInfo() throws SQLException {
        checkState(started.get(), "not yet started!");

        DatabaseInfo databaseInfo = instanceProvider.get();
        if (databaseInfo.exception().isEmpty()) {
            return databaseInfo;
        } else {
            throw databaseInfo.exception().get();
        }
    }

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

        public InstanceProviderPipeline() {
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
                    final String newDbName = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ROOT);
                    try {
                        createDatabase(pg.createDefaultDataSource(), newDbName, PG_DEFAULT_USER);
                        nextDatabase.put(DatabaseInfo.builder().dbName(newDbName).port(pg.getPort()).properties(pg.getConnectionProperties()).build());
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
                    LOG.warn("Caught Throwable in loop:", t);
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

    private static void createDatabase(final DataSource dataSource, final String databaseName, final String user) throws SQLException {
        try (Connection c = dataSource.getConnection();
                Statement stmt = c.createStatement()) {
            stmt.executeUpdate(format("CREATE DATABASE %s OWNER %s ENCODING = 'utf8'", databaseName, user));
        }
    }

    public abstract static class Builder<T> {

        protected DatabasePreparer databasePreparer = DatabasePreparer.NOOP_PREPARER;
        protected ImmutableSet.Builder<EmbeddedPostgres.BuilderCustomizer> customizers = ImmutableSet.builder();
        protected final boolean multiMode;

        protected Builder(boolean multiMode) {
            this.multiMode = multiMode;
        }

        public Builder<T> withPreparer(DatabasePreparer databasePreparer) {
            this.databasePreparer = checkNotNull(databasePreparer, "databasePreparer is null");
            return this;
        }

        public Builder<T> withCustomizer(EmbeddedPostgres.BuilderCustomizer customizer) {
            this.customizers.add(checkNotNull(customizer, "customizer is null"));
            return this;
        }

        public abstract T build();
    }

    public static class DatabaseManagerBuilder extends Builder<DatabaseManager> {

        public DatabaseManagerBuilder(boolean multiMode) {
            super(multiMode);
        }

        @Override
        public DatabaseManager build() {
            return new DatabaseManager(databasePreparer, customizers.build(), multiMode);
        }
    }
}