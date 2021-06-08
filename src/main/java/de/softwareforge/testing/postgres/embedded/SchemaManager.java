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
import static de.softwareforge.testing.postgres.embedded.SchemaInfo.PG_DEFAULT_USER;
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
 * Manages the various schemas within a postgres instance.
 */
public class SchemaManager implements AutoCloseable {

    public static final Logger LOG = LoggerFactory.getLogger(SchemaManager.class);

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();

    private final SchemaPreparer schemaPreparer;
    private final Set<EmbeddedPostgres.BuilderCustomizer> customizers;
    private final boolean multiMode;

    private volatile SchemaProvider schemaProvider = null;
    private volatile EmbeddedPostgres pg = null;

    private SchemaManager(SchemaPreparer schemaPreparer,
            Set<EmbeddedPostgres.BuilderCustomizer> customizers,
            boolean multiMode) {
        this.schemaPreparer = checkNotNull(schemaPreparer, "schemaPreparer is null");
        this.customizers = checkNotNull(customizers, "customizers is null");
        this.multiMode = multiMode;
    }

    public static Builder<SchemaManager> multiSchema() {
        return new PreparedDbProviderBuilder(true);
    }

    public static Builder<SchemaManager> singleSchema() {
        return new PreparedDbProviderBuilder(false);
    }

    public SchemaManager start() throws IOException, SQLException {
        if (!started.getAndSet(true)) {
            EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder();
            for (BuilderCustomizer customizer : customizers) {
                customizer.customize(builder);
            }

            this.pg = builder.build();

            DataSource dataSourceToPrepare = multiMode ? pg.getTemplateDatabase() : pg.getDatabase();
            schemaPreparer.prepare(dataSourceToPrepare);

            this.schemaProvider = multiMode ? new SchemaProviderPipeline() : () -> pg.getConnectionInfo();

            this.schemaProvider.start();
        }

        return this;
    }

    @Override
    public void close() throws Exception {
        checkState(started.get(), "not yet started!");
        if (!closed.getAndSet(true)) {
            schemaProvider.close();
            pg.close();
        }
    }

    public SchemaInfo getConnectionInfo() throws SQLException {
        checkState(started.get(), "not yet started!");

        SchemaInfo schemaInfo = schemaProvider.get();
        if (schemaInfo.exception().isEmpty()) {
            return schemaInfo;
        } else {
            throw schemaInfo.exception().get();
        }
    }

    public EmbeddedPostgres getEmbeddedPostgres() {
        checkState(started.get(), "not yet started!");
        return pg;
    }

    private interface SchemaProvider extends Supplier<SchemaInfo>, AutoCloseable {

        default void start() {
        }

        @Override
        default void close() {
        }

        SchemaInfo get();
    }

    private final class SchemaProviderPipeline implements SchemaProvider, Runnable {

        private final ExecutorService executor;
        private final SynchronousQueue<SchemaInfo> nextDatabase = new SynchronousQueue<>();

        private final AtomicBoolean closed = new AtomicBoolean();

        public SchemaProviderPipeline() {
            this.executor = Executors.newSingleThreadExecutor(
                    new ThreadFactoryBuilder()
                            .setDaemon(true)
                            .setNameFormat("schema-creator-" + pg.instanceId() + "-%d")
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
                        createDatabase(pg.getDatabase(), newDbName, PG_DEFAULT_USER);
                        nextDatabase.put(SchemaInfo.builder().dbName(newDbName).port(pg.getPort()).properties(pg.getConnectConfig()).build());
                    } catch (SQLException e) {
                        // https://www.postgresql.org/docs/13/errcodes-appendix.html - 57P01 admin_shutdown
                        if (!e.getSQLState().equals("57P01")) {
                            LOG.warn("Caught SQL Exception:", e);
                            nextDatabase.put(SchemaInfo.forException(e));
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
        public SchemaInfo get() {
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

        protected SchemaPreparer schemaPreparer = SchemaPreparer.NOOP_PREPARER;
        protected ImmutableSet.Builder<EmbeddedPostgres.BuilderCustomizer> customizers = ImmutableSet.builder();
        protected final boolean multiMode;

        protected Builder(boolean multiMode) {
            this.multiMode = multiMode;
        }

        public Builder<T> withPreparer(SchemaPreparer schemaPreparer) {
            this.schemaPreparer = checkNotNull(schemaPreparer, "schemaPreparer is null");
            return this;
        }

        public Builder<T> withCustomizer(EmbeddedPostgres.BuilderCustomizer customizer) {
            this.customizers.add(checkNotNull(customizer, "customizer is null"));
            return this;
        }

        public abstract T build();
    }

    public static class PreparedDbProviderBuilder extends Builder<SchemaManager> {

        public PreparedDbProviderBuilder(boolean useTemplate) {
            super(useTemplate);
        }

        @Override
        public SchemaManager build() {
            return new SchemaManager(schemaPreparer, customizers.build(), multiMode);
        }
    }
}
