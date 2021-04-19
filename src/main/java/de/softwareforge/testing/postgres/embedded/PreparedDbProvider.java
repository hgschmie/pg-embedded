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

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.softwareforge.testing.postgres.embedded.EmbeddedPostgres.Builder;
import org.apache.commons.lang3.RandomStringUtils;
import org.postgresql.ds.PGSimpleDataSource;

import static com.google.common.base.Preconditions.checkNotNull;
import static de.softwareforge.testing.postgres.embedded.EmbeddedPostgres.JDBC_FORMAT;
import static de.softwareforge.testing.postgres.embedded.EmbeddedPostgres.LOCALHOST_SERVERNAMES;

public class PreparedDbProvider {

    /**
     * Each database cluster's <code>template1</code> database has a unique set of schema loaded so that the databases may be cloned.
     */
    private static final ConcurrentMap<ClusterKey, PrepPipeline> CLUSTERS = new ConcurrentHashMap<>();

    private final PrepPipeline dbPreparer;

    public static PreparedDbProvider forPreparer(DatabasePreparer preparer) throws SQLException, IOException {
        checkNotNull(preparer, "preparer is null");

        return forPreparer(preparer, ImmutableList.of());
    }

    public static PreparedDbProvider forPreparer(DatabasePreparer preparer, Iterable<Consumer<EmbeddedPostgres.Builder>> customizers)
            throws SQLException, IOException {
        checkNotNull(preparer, "preparer is null");
        checkNotNull(customizers, "customizers is null");

        return new PreparedDbProvider(preparer, customizers);
    }

    private PreparedDbProvider(DatabasePreparer preparer, Iterable<Consumer<Builder>> customizers) throws SQLException, IOException {
        checkNotNull(preparer, "preparer is null");
        checkNotNull(customizers, "customizers is null");

        dbPreparer = createOrFindPreparer(preparer, customizers);
    }

    /**
     * Each schema set has its own database cluster.  The template1 database has the schema preloaded so that each test case need only create a new database and
     * not re-invoke your preparer.
     *
     * @param preparer    A preparer for the datasource to preconfigure the data source before use.
     * @param customizers Customizers to customize the {@link EmbeddedPostgres.Builder} that creates the database.
     * @return pipelined preparer for the data source.
     * @throws IOException  If the database could not be started.
     * @throws SQLException If an error happens during initialization of the data source.
     */
    private static synchronized PrepPipeline createOrFindPreparer(DatabasePreparer preparer, Iterable<Consumer<Builder>> customizers)
            throws IOException, SQLException {
        checkNotNull(preparer, "preparer is null");
        checkNotNull(customizers, "customizers is null");

        final Builder builder = EmbeddedPostgres.builder();
        customizers.forEach(c -> c.accept(builder));
        // create the cluster key after the builder has been customized
        // this ensures that the exact same builder is used for the key
        // and to create the instance if necessary
        final ClusterKey key = new ClusterKey(builder, preparer);
        try {
            return CLUSTERS.computeIfAbsent(key, k -> {
                try {
                    final EmbeddedPostgres pg = builder.build(); //NOPMD
                    preparer.prepare(pg.getTemplateDatabase());
                    return new PrepPipeline(pg).start();
                } catch (IOException | SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() != null) {
                Throwables.propagateIfPossible(e.getCause(), IOException.class);
                Throwables.propagateIfPossible(e.getCause(), SQLException.class);
            }
            throw e;
        }
    }

    /**
     * Create a new database, and return it as a JDBC connection string. NB: No two invocations will return the same database.
     *
     * @return The JDBI connection string for a new database.
     * @throws SQLException If an error happens during initialization of the data source.
     */
    public String createDatabase() throws SQLException {
        return getJdbcUri(createNewDB());
    }

    /**
     * Create a new database, and return the backing info. This allows you to access the host and port. More common usage is to call createDatabase() and get
     * the JDBC connection string. NB: No two invocations will return the same database.
     * @throws SQLException If an error happens during initialization of the data source.
     *
     * @return The connection information for the new database.
     */
    private DbInfo createNewDB() throws SQLException {
        return dbPreparer.getNextDb();
    }

    public ConnectionInfo createNewDatabase() throws SQLException {
        final DbInfo dbInfo = createNewDB();
        return dbInfo == null || !dbInfo.isSuccess() ? null
                : new ConnectionInfo(dbInfo.getDbName(), dbInfo.getPort(), dbInfo.getUser(), dbInfo.getProperties());
    }

    /**
     * Create a new Datasource given ConnectionInfo. More common usage is to call createDatasource().
     * @param connectionInfo {@link ConnectionInfo} describing the datasource.
     *
     * @return A {@link DataSource} object for the {@link ConnectionInfo}.
     * @throws SQLException If an error happens during initialization of the data source.
     */
    public DataSource createDataSourceFromConnectionInfo(final ConnectionInfo connectionInfo) throws SQLException {
        checkNotNull(connectionInfo, "connectionInfo is null");

        final PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerNames(LOCALHOST_SERVERNAMES);
        ds.setPortNumbers(new int[]{connectionInfo.getPort()});
        ds.setDatabaseName(connectionInfo.getDbName());
        ds.setUser(connectionInfo.getUser());

        for (Entry<String, String> entry : connectionInfo.getProperties().entrySet()) {
            ds.setProperty(entry.getKey(), entry.getValue());
        }

        return ds;
    }

    /**
     * Create a new database, and return it as a DataSource. No two invocations will return the same database.
     *
     * @return An initialized {@link DataSource} object.
     * @throws SQLException If an error happens during initialization of the data source.
     */
    public DataSource createDataSource() throws SQLException {
        return createDataSourceFromConnectionInfo(createNewDatabase());
    }

    private String getJdbcUri(DbInfo dbInfo) {
        checkNotNull(dbInfo, "dbInfo is null");

        String additionalParameters = dbInfo.getProperties().entrySet().stream()
                .map(e -> String.format("&%s=%s", e.getKey(), e.getValue()))
                .collect(Collectors.joining());
        return String.format(JDBC_FORMAT, dbInfo.port, dbInfo.dbName, dbInfo.user) + additionalParameters;
    }

    /**
     * Spawns a background thread that prepares databases ahead of time for speed, and then uses a synchronous queue to hand the prepared databases off to test
     * cases.
     */
    private static class PrepPipeline implements Runnable {

        private final EmbeddedPostgres pg;
        private final SynchronousQueue<DbInfo> nextDatabase = new SynchronousQueue<>();

        PrepPipeline(EmbeddedPostgres pg) {
            this.pg = checkNotNull(pg, "pg is null");
        }

        PrepPipeline start() {
            final ExecutorService service = Executors.newSingleThreadExecutor(r -> {
                final Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("cluster-" + pg + "-preparer");
                return t;
            });
            service.submit(this);
            service.shutdown();
            return this;
        }

        DbInfo getNextDb() throws SQLException {
            try {
                final DbInfo next = nextDatabase.take();
                if (next.ex != null) {
                    throw next.ex;
                }
                return next;
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void run() {
            while (true) {
                final String newDbName = RandomStringUtils.randomAlphabetic(12).toLowerCase(Locale.ENGLISH);
                SQLException failure = null;
                try {
                    create(pg.getPostgresDatabase(), newDbName, "postgres");
                } catch (SQLException e) {
                    failure = e;
                }
                try {
                    if (failure == null) {
                        nextDatabase.put(DbInfo.ok(newDbName, pg.getPort(), "postgres", pg.getConnectConfig()));
                    } else {
                        nextDatabase.put(DbInfo.error(failure));
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void create(final DataSource dataSource, final String databaseName, final String user) throws SQLException {
        checkNotNull(dataSource, "dataSource is null");
        checkNotNull(databaseName, "databaseName is null");
        checkNotNull(user, "user is null");

        try (Connection c = dataSource.getConnection();
                PreparedStatement stmt = c.prepareStatement(String.format("CREATE DATABASE %s OWNER %s ENCODING = 'utf8'", databaseName, user))) {
            stmt.execute();
        }
    }

    private static class ClusterKey {

        private final DatabasePreparer preparer;
        private final Builder builder;

        ClusterKey(EmbeddedPostgres.Builder builder, DatabasePreparer preparer) {
            this.builder = checkNotNull(builder, "builder is null");
            this.preparer = checkNotNull(preparer, "preparer is null");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClusterKey that = (ClusterKey) o;
            return preparer.equals(that.preparer) && builder.equals(that.builder);
        }

        @Override
        public int hashCode() {
            return Objects.hash(preparer, builder);
        }
    }

    public static class DbInfo {

        public static DbInfo ok(final String dbName, final int port, final String user) {
            return ok(dbName, port, user, ImmutableMap.of());
        }

        private static DbInfo ok(final String dbName, final int port, final String user, final Map<String, String> properties) {
            return new DbInfo(dbName, port, user, properties, null);
        }

        public static DbInfo error(SQLException e) {
            return new DbInfo(null, -1, null, ImmutableMap.of(), e);
        }

        private final String dbName;
        private final int port;
        private final String user;
        private final ImmutableMap<String, String> properties;
        private final SQLException ex;

        private DbInfo(final String dbName, final int port, final String user, final Map<String, String> properties, final SQLException e) {
            this.dbName = dbName;
            this.port = port;
            this.user = user;
            this.properties = ImmutableMap.copyOf(properties);
            this.ex = e;
        }

        public int getPort() {
            return port;
        }

        public String getDbName() {
            return dbName;
        }

        public String getUser() {
            return user;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public SQLException getException() {
            return ex;
        }

        public boolean isSuccess() {
            return ex == null;
        }
    }
}
