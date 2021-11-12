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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static de.softwareforge.testing.postgres.embedded.DatabaseInfo.PG_DEFAULT_DB;
import static de.softwareforge.testing.postgres.embedded.DatabaseInfo.PG_DEFAULT_USER;
import static de.softwareforge.testing.postgres.embedded.EmbeddedUtil.formatDuration;
import static java.lang.String.format;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.sql.DataSource;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages an embedded PostgreSQL server instance.
 */
public final class EmbeddedPostgres implements AutoCloseable {

    static final String[] LOCALHOST_SERVER_NAMES = new String[]{"localhost"};

    private static final String PG_TEMPLATE_DB = "template1";

    @VisibleForTesting
    public static final Duration DEFAULT_PG_STARTUP_WAIT = Duration.ofSeconds(10);

    // folders need to be at least 10 minutes old to be considered for deletion.
    private static final long MINIMUM_AGE_IN_MS = Duration.ofMinutes(10).toMillis();

    // prefix for data folders in the parent that might be deleted
    private static final String DATA_DIRECTORY_PREFIX = "epd-";

    private static final String PG_STOP_MODE = "fast";
    private static final String PG_STOP_WAIT_SECONDS = "5";
    static final String LOCK_FILE_NAME = "epg-lock";

    private final Logger logger;

    private final String instanceId;
    private final File pgDir;
    private final File dataDirectory;

    private final Duration serverStartupWait;
    private final int port;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final ImmutableMap<String, String> serverConfiguration;
    private final ImmutableMap<String, String> localeConfiguration;
    private final ImmutableMap<String, String> connectionProperties;

    private final File lockFile;
    private volatile FileOutputStream lockStream;
    private volatile FileLock lock;

    private final boolean removeDataOnShutdown;

    private final ProcessBuilder.Redirect errorRedirector;
    private final ProcessBuilder.Redirect outputRedirector;


    /**
     * Returns an instance that has been started and configured. The {@link Builder#withDefaults()} configuration has been applied.
     */
    public static EmbeddedPostgres defaultInstance() throws IOException {
        return builderWithDefaults().build();
    }

    /**
     * Returns a builder with default {@link Builder#withDefaults()} configuration already applied.
     */
    public static EmbeddedPostgres.Builder builderWithDefaults() {
        return new Builder().withDefaults();
    }

    /**
     * Returns a new {@link Builder}.
     */
    public static EmbeddedPostgres.Builder builder() {
        return new Builder();
    }

    private EmbeddedPostgres(
            final String instanceId,
            final File postgresInstallDirectory,
            final File dataDirectory,
            final boolean removeDataOnShutdown,
            final Map<String, String> serverConfiguration,
            final Map<String, String> localeConfiguration,
            final Map<String, String> connectionProperties,
            final int port,
            final ProcessBuilder.Redirect errorRedirector,
            final ProcessBuilder.Redirect outputRedirector,
            final Duration serverStartupWait) {

        this.instanceId = checkNotNull(instanceId, "instanceId is null");

        this.logger = LoggerFactory.getLogger(toString());

        this.pgDir = checkNotNull(postgresInstallDirectory, "postgresInstallDirectory is null");
        this.dataDirectory = checkNotNull(dataDirectory, "dataDirectory is null");

        this.removeDataOnShutdown = removeDataOnShutdown;

        this.serverConfiguration = ImmutableMap.copyOf(checkNotNull(serverConfiguration, "serverConfiguration is null"));
        this.localeConfiguration = ImmutableMap.copyOf(checkNotNull(localeConfiguration, "localeConfiguration is null"));
        this.connectionProperties = ImmutableMap.copyOf(checkNotNull(connectionProperties, "connectionProperties is null"));

        this.port = port;

        this.errorRedirector = checkNotNull(errorRedirector, "errorRedirector is null");
        this.outputRedirector = checkNotNull(outputRedirector, "outputRedirector is null");

        this.serverStartupWait = checkNotNull(serverStartupWait, "serverStartupWait is null");
        this.lockFile = new File(this.dataDirectory, LOCK_FILE_NAME);

        logger.debug(format("data dir is %s, install dir is %s", this.dataDirectory, this.pgDir));
    }

    /**
     * Creates a {@link DatabaseInfo} object describing the default database (the <pre>postgres</pre>).
     */
    public DatabaseInfo createDefaultDatabaseInfo() {
        return DatabaseInfo.builder().port(getPort()).properties(this.connectionProperties).build();
    }

    /**
     * Creates a {@link DataSource} object that connects to the standard system database (normally <pre>template1</pre>).
     * <p>
     * Any modification to this database will be propagated to any new database that is created with <pre>CREATE DATABASE...</pre> unless another database is
     * explicitly named as the template..
     */
    public DataSource createTemplateDataSource() throws SQLException {
        return createDataSource(PG_DEFAULT_USER, PG_TEMPLATE_DB, getPort(), this.connectionProperties);
    }

    /**
     * Creates a {@link DataSource} object that connects to the default database (<pre>postgres</pre>).
     */
    public DataSource createDefaultDataSource() throws SQLException {
        return createDataSource(PG_DEFAULT_USER, PG_DEFAULT_DB, getPort(), this.connectionProperties);
    }

    /**
     * Creates a {@link DataSource} with a specific user and database name.
     * <p>
     * Creating the DataSource does <b>not</b> create the database or the user itself. This must be done by the calling code (e.g. with a {@link
     * EmbeddedPostgresPreparer}).
     */
    public DataSource createDataSource(String user, String databaseName) throws SQLException {
        return createDataSource(user, databaseName, getPort(), this.connectionProperties);
    }

    static DataSource createDataSource(String user, String databaseName, int port, Map<String, String> properties) throws SQLException {
        checkNotNull(user, "user is null");
        checkNotNull(databaseName, "databaseName is null");
        checkNotNull(properties, "properties is null");

        final PGSimpleDataSource ds = new PGSimpleDataSource();

        ds.setServerNames(LOCALHOST_SERVER_NAMES);
        ds.setPortNumbers(new int[]{port});
        ds.setDatabaseName(databaseName);
        ds.setUser(user);

        for (final Entry<String, String> entry : properties.entrySet()) {
            ds.setProperty(entry.getKey(), entry.getValue());
        }

        return ds;
    }

    /**
     * Returns the network port for the PostgreSQL server instance.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the connection properties for the PostgreSQL server instance.
     */
    ImmutableMap<String, String> getConnectionProperties() {
        return connectionProperties;
    }

    /**
     * Returns the instance id for the PostgreSQL server instance. This id is an alphanumeric string that can be used to differentiate between multiple embedded
     * PostgreSQL server instances.
     */
    public String instanceId() {
        return instanceId;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "$" + this.instanceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EmbeddedPostgres that = (EmbeddedPostgres) o;
        return instanceId.equals(that.instanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId);
    }

    // internal methods


    private void boot() throws IOException {
        EmbeddedUtil.mkdirs(this.dataDirectory);

        if (this.removeDataOnShutdown || !new File(this.dataDirectory, "postgresql.conf").exists()) {
            initDatabase();
        }

        lock();

        startDatabase();
    }


    private synchronized void lock() throws IOException {
        this.lockStream = new FileOutputStream(this.lockFile);
        this.lock = lockStream.getChannel().tryLock();
        checkState(lock != null, "could not lock %s", lockFile);
    }

    private synchronized void unlock() throws IOException {
        if (lock != null) {
            lock.release();
        }
        Closeables.close(lockStream, true);
    }

    private void initDatabase() throws IOException {
        ImmutableList.Builder<String> commandBuilder = ImmutableList.builder();
        commandBuilder.add(pgBin("initdb"))
                .addAll(createLocaleOptions())
                .add("-A", "trust",
                        "-U", PG_DEFAULT_USER,
                        "-D", this.dataDirectory.getPath(),
                        "-E", "UTF-8");
        final Stopwatch watch = system(commandBuilder.build());
        logger.debug(format("initdb completed in %s", formatDuration(watch.elapsed())));
    }

    private void startDatabase() throws IOException {
        checkState(!started.getAndSet(true), "database already started!");

        final ImmutableList.Builder<String> commandBuilder = ImmutableList.builder();
        commandBuilder.add(pgBin("pg_ctl"),
                "-D", this.dataDirectory.getPath(),
                "-o", createInitOptions().stream().collect(Collectors.joining(" ")),
                "start"
        );

        final Stopwatch watch = Stopwatch.createStarted();
        final Process postmaster = spawn("pg", commandBuilder.build());

        logger.info(format("started as pid %d on port %d", postmaster.pid(), port));
        logger.debug(format("Waiting up to %s for server startup to finish", formatDuration(serverStartupWait)));

        Runtime.getRuntime().addShutdownHook(newCloserThread());

        checkState(waitForServerStartup(), "Could not start pg, interrupted?");
        logger.debug(format("startup complete in %s", formatDuration(watch.elapsed())));
    }

    private void stopDatabase(File dataDirectory) throws IOException {
        final ImmutableList.Builder<String> commandBuilder = ImmutableList.builder();
        commandBuilder.add(pgBin("pg_ctl"),
                "-D", dataDirectory.getPath(),
                "stop",
                "-m", PG_STOP_MODE,
                "-t", PG_STOP_WAIT_SECONDS, "-w");

        final Stopwatch watch = system(commandBuilder.build());
        logger.debug(format("shutdown complete in %s", formatDuration(watch.elapsed())));
    }

    private List<String> createInitOptions() {
        final ImmutableList.Builder<String> initOptions = ImmutableList.builder();
        initOptions.add(
                "-p", Integer.toString(port),
                "-F");

        serverConfiguration.forEach((k, v) -> {
            initOptions.add("-c");
            initOptions.add(k + "=" + v);
        });

        return initOptions.build();
    }

    @VisibleForTesting
    List<String> createLocaleOptions() {
        final ImmutableList.Builder<String> localeOptions = ImmutableList.builder();

        localeConfiguration.forEach((key, value) -> {
            if (value.length() > 0) {
                localeOptions.add("--" + key + "=" + value);
            } else {
                localeOptions.add("--" + key);
            }
        });
        return localeOptions.build();
    }

    private boolean waitForServerStartup() throws IOException {
        Throwable lastCause = null;
        final long start = System.nanoTime();
        final long maxWaitNs = TimeUnit.NANOSECONDS.convert(serverStartupWait.toMillis(), TimeUnit.MILLISECONDS);
        while (System.nanoTime() - start < maxWaitNs) {
            try {
                if (verifyReady()) {
                    return true;
                }
            } catch (final SQLException e) {
                lastCause = e;
                logger.trace("while waiting for server startup:", e);
            }

            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        throw new IOException("Gave up waiting for server to start after " + serverStartupWait.toMillis() + "ms", lastCause);
    }

    private boolean verifyReady() throws IOException, SQLException {
        // check TCP connection
        final InetAddress localhost = InetAddress.getLoopbackAddress();
        try (Socket sock = new Socket()) {
            sock.setSoTimeout((int) Duration.ofMillis(500).toMillis());
            sock.connect(new InetSocketAddress(localhost, port), (int) Duration.ofMillis(500).toMillis());
        } catch (ConnectException e) {
            return false;
        }

        // check JDBC connection
        try (Connection c = createDefaultDataSource().getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT 1")) {
            checkState(rs.next(), "expecting single row");
            checkState(rs.getInt(1) == 1, "expecting 1 as result");
            checkState(!rs.next(), "expecting single row");
            return true;
        }
    }

    private Thread newCloserThread() {
        final Thread closeThread = new Thread(() -> {
            try {
                EmbeddedPostgres.this.close();
            } catch (IOException e) {
                logger.trace("while closing instance:", e);
            }
        });

        closeThread.setName("pg-closer");
        return closeThread;
    }

    /**
     * Closing an {@link EmbeddedPostgres} instance shuts down the connected database instance.
     */
    @Override
    public void close() throws IOException {
        if (closed.getAndSet(true)) {
            return;
        }

        try {
            stopDatabase(this.dataDirectory);
        } catch (final Exception e) {
            logger.error("could not stop pg:", e);
        }

        unlock();

        if (removeDataOnShutdown) {
            try {
                EmbeddedUtil.rmdirs(dataDirectory);
            } catch (Exception e) {
                logger.error(format("Could not clean up directory %s:", dataDirectory.getAbsolutePath()), e);
            }
        } else {
            logger.debug(format("preserved data directory %s", dataDirectory.getAbsolutePath()));
        }
    }

    @VisibleForTesting
    File getDataDirectory() {
        return dataDirectory;
    }

    @VisibleForTesting
    Map<String, String> getLocaleConfiguration() {
        return localeConfiguration;
    }


    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    private void cleanOldDataDirectories(File parentDirectory) {
        final File[] children = parentDirectory.listFiles();
        if (children == null) {
            return;
        }
        for (final File dir : children) {
            if (!dir.isDirectory()) {
                continue;
            }

            // only ever touch known data directories.
            if (!dir.getName().startsWith(DATA_DIRECTORY_PREFIX)) {
                continue;
            }

            // only touch data directories that hold a lock file.
            final File lockFile = new File(dir, LOCK_FILE_NAME);
            if (!lockFile.exists()) {
                continue;
            }

            // file must have a minimum age. This can not be the same check as
            // the exists b/c non-existent files return 0 (epoch) as lastModified so
            // they are considered "ancient".
            if (System.currentTimeMillis() - lockFile.lastModified() < MINIMUM_AGE_IN_MS) {
                continue;
            }

            try (FileOutputStream fos = new FileOutputStream(lockFile);
                    FileLock lock = fos.getChannel().tryLock()) {
                if (lock != null) {
                    logger.debug(format("found stale data directory %s", dir));
                    if (new File(dir, "postmaster.pid").exists()) {
                        try {
                            stopDatabase(dir);
                            logger.debug("shutting down orphaned database!");
                        } catch (Exception e) {
                            logger.warn(format("failed to orphaned database in %s:", dir), e);
                        }
                    }
                    EmbeddedUtil.rmdirs(dir);
                }
            } catch (final OverlappingFileLockException e) {
                // The directory belongs to another instance in this VM.
                logger.trace("while cleaning old data directories:", e);
            } catch (final Exception e) {
                logger.warn("while cleaning old data directories:", e);
            }
        }
    }

    private String pgBin(String binaryName) {
        final String extension = EmbeddedUtil.IS_OS_WINDOWS ? ".exe" : "";
        return new File(this.pgDir, "bin/" + binaryName + extension).getPath();
    }

    private Process spawn(@Nullable String processName, List<String> commandAndArgs) throws IOException {
        final ProcessBuilder builder = new ProcessBuilder(commandAndArgs);
        builder.redirectErrorStream(true);
        builder.redirectError(errorRedirector);
        builder.redirectOutput(outputRedirector);
        final Process process = builder.start();

        processName = processName != null ? processName : process.info().command().map(EmbeddedUtil::getFileBaseName).orElse("<unknown>");
        String name = format("%s (%d)", processName, process.pid());

        ProcessOutputLogger.logOutput(logger, name, process);
        return process;
    }


    private Stopwatch system(List<String> commandAndArgs) throws IOException {
        checkArgument(commandAndArgs.size() > 0, "No commandAndArgs given!");
        String prefix = EmbeddedUtil.getFileBaseName(commandAndArgs.get(0));

        Stopwatch watch = Stopwatch.createStarted();
        Process process = spawn(prefix, commandAndArgs);
        try {
            if (process.waitFor() != 0) {
                try (InputStreamReader reader = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
                    throw new IllegalStateException(format("Process %s failed%n%s",
                            commandAndArgs, CharStreams.toString(reader)));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return watch;
    }


    /**
     * Callback interface to customize a builder during creation.
     *
     * @deprecated Use {@link EmbeddedPostgresPreparer} with {@link Builder} as type parameter.
     */
    @Deprecated
    @FunctionalInterface
    public interface BuilderCustomizer {

        /**
         * Callback to customize a given {@link Builder}.
         *
         * @param builder The builder instance. Any method on the builder can be called.
         * @throws SQLException For any SQL related problems.
         * @throws IOException  For any IO related problem.
         */
        void customize(Builder builder) throws IOException, SQLException;
    }

    /**
     * Creates a new {@link EmbeddedPostgres} instance and starts it.
     */
    public static class Builder {

        private File installationDirectory = null;
        private File dataDirectory = null;

        private final Map<String, String> serverConfiguration = new HashMap<>();
        private final Map<String, String> localeConfiguration = new HashMap<>();
        private boolean removeDataOnShutdown = true;
        private int port = 0;
        private final Map<String, String> connectionProperties = new HashMap<>();
        private PgDirectoryResolver directoryResolver = UncompressBundleDirectoryResolver.getDefault();
        private Duration serverStartupWait = DEFAULT_PG_STARTUP_WAIT;

        private ProcessBuilder.Redirect errRedirector = ProcessBuilder.Redirect.PIPE;
        private ProcessBuilder.Redirect outRedirector = ProcessBuilder.Redirect.PIPE;

        Builder() {
        }

        /**
         * Apply a set of defaults to the database server:
         * <ul>
         *     <li>timezone: UTC</li>
         *     <li>synchronous_commit: off</li>
         *     <li>max_connections: 300</li>
         * </ul>
         *
         * @return The builder itself.
         */
        public Builder withDefaults() {
            serverConfiguration.put("timezone", "UTC");
            serverConfiguration.put("synchronous_commit", "off");
            serverConfiguration.put("max_connections", "300");
            return this;
        }

        /**
         * Sets the time that the builder will wait for the PostgreSQL server instance to start. Default is 10 seconds.
         *
         * @param serverStartupWait Startup wait time. Must not be null or negative.
         * @return The builder itself.
         */
        public Builder setServerStartupWait(Duration serverStartupWait) {
            checkNotNull(serverStartupWait, "serverStartupWait is null");
            checkArgument(!serverStartupWait.isNegative(), "Negative durations are not permitted.");

            this.serverStartupWait = serverStartupWait;
            return this;
        }

        /**
         * Whether to remove the data directory on server shutdown. If true, the contents of the data directory are deleted when the {@link EmbeddedPostgres}
         * instance is closed. Default is true.
         *
         * @param removeDataOnShutdown True removes the contents of the data directory on shutdown.
         * @return The builder itself.
         */
        public Builder setRemoveDataOnShutdown(boolean removeDataOnShutdown) {
            this.removeDataOnShutdown = removeDataOnShutdown;
            return this;
        }

        /**
         * Explicitly set the location of the data directory. Default is using a managed directory.
         *
         * @param dataDirectory The directory to use. Must not be null. If it exists, the current user must be able to access the directory for reading and
         *                      writing. If the directory does not exist then the current user must be able to create it for reading and writing.
         * @return The builder itself.
         */
        public Builder setDataDirectory(Path dataDirectory) {
            checkNotNull(dataDirectory, "dataDirectory is null");
            return setDataDirectory(dataDirectory.toFile());
        }

        /**
         * Explicitly set the location of the data directory. Default is using a managed directory.
         *
         * @param dataDirectory The directory to use. Must not be null. If it exists, the current user must be able to access the directory for reading and
         *                      writing. If the directory does not exist then the current user must be able to create it for reading and writing.
         * @return The builder itself.
         */
        public Builder setDataDirectory(String dataDirectory) {
            checkNotNull(dataDirectory, "dataDirectory is null");
            return setDataDirectory(new File(dataDirectory));
        }

        /**
         * Explicitly set the location of the data directory. Default is using a managed directory.
         *
         * @param dataDirectory The directory to use. Must not be null. If it exists, the current user must be able to access the directory for reading and
         *                      writing. If the directory does not exist then the current user must be able to create it for reading and writing.
         * @return The builder itself.
         */
        public Builder setDataDirectory(File dataDirectory) {
            this.dataDirectory = checkNotNull(dataDirectory, "dataDirectory is null");
            return this;
        }

        /**
         * Adds a server configuration parameter. All parameters are passed to the PostgreSQL server a startup using the <code>postgres</code> command.
         * <p>
         * Values and their function are specific to the PostgreSQL version selected.
         * <p>
         * See https://www.postgresql.org/docs/13/runtime-config.html for more information.
         *
         * @param key   Configuration parameter name. Must not be null.
         * @param value Configuration parameter value. Must not be null.
         * @return The builder itself.
         */
        public Builder addServerConfiguration(String key, String value) {
            checkNotNull(key, "key is null");
            checkNotNull(value, "value is null");
            this.serverConfiguration.put(key, value);
            return this;
        }

        /**
         * @deprecated Use {@link #addInitDbConfiguration} instead.
         */
        @Deprecated
        public Builder addLocaleConfiguration(String key, String value) {
            checkNotNull(key, "key is null");
            checkNotNull(value, "value is null");
            this.localeConfiguration.put(key, value);
            return this;
        }

        /**
         * Adds a configuration parameters for the <code>initdb</code> command that is used to create the PostgreSQL server.
         * <p>
         * Each value is added as a command line parameter to the command.
         * <p>
         * See https://www.postgresql.org/docs/13/app-initdb.html for an overview of possible values.
         *
         * @param key   initdb parameter name. Must not be null.
         * @param value initdb parameter value. Must not be null. When the empty string is used as the value, the resulting command line parameter will not have
         *              a equal sign and a value assigned.
         * @return The builder itself.
         */
        public Builder addInitDbConfiguration(String key, String value) {
            checkNotNull(key, "key is null");
            checkNotNull(value, "value is null");
            this.localeConfiguration.put(key, value);
            return this;
        }

        /**
         * @param key
         * @param value
         * @return The builder itself.
         */
        public Builder addConnectionProperty(String key, String value) {
            checkNotNull(key, "key is null");
            checkNotNull(value, "value is null");
            this.connectionProperties.put(key, value);
            return this;
        }

        /**
         * @param installationDirectory
         * @return The builder itself.
         */
        public Builder setInstallationDirectory(File installationDirectory) {
            checkNotNull(installationDirectory, "workingDirectory is null");
            this.installationDirectory = installationDirectory;
            return this;
        }

        /**
         * @param port
         * @return The builder itself.
         */
        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        /**
         * @param errRedirector
         * @return The builder itself.
         */
        public Builder setErrorRedirector(ProcessBuilder.Redirect errRedirector) {
            this.errRedirector = checkNotNull(errRedirector, "errRedirector is null");
            return this;
        }

        /**
         * @param outRedirector
         * @return The builder itself.
         */
        public Builder setOutputRedirector(ProcessBuilder.Redirect outRedirector) {
            this.outRedirector = checkNotNull(outRedirector, "outRedirector is null");
            return this;
        }

        /**
         * @param directoryResolver
         * @return The builder itself.
         */
        public Builder setPostgresDirectoryResolver(PgDirectoryResolver directoryResolver) {
            this.directoryResolver = checkNotNull(directoryResolver, "directoryResolver is null");
            return this;
        }

        /**
         * @param directory
         * @return The builder itself.
         */
        public Builder setPostgresBinaryDirectory(File directory) {
            checkNotNull(directory, "directory is null");
            return setPostgresDirectoryResolver((x) -> directory);
        }

        /**
         * @return
         * @throws IOException
         */
        public EmbeddedPostgres build() throws IOException {
            // Builder Id
            final String instanceId = EmbeddedUtil.randomAlphaNumeric(16);

            int port = this.port != 0 ? this.port : EmbeddedUtil.allocatePort();

            // installation root if nothing has been set by the user.
            final File parentDirectory = EmbeddedUtil.getWorkingDirectory();
            EmbeddedUtil.mkdirs(parentDirectory);

            final File installationDirectory = MoreObjects.firstNonNull(this.installationDirectory, parentDirectory);
            final File postgresInstallDirectory = directoryResolver.getDirectory(installationDirectory);

            final File dataDirectory;
            if (this.dataDirectory != null) {
                dataDirectory = this.dataDirectory;
            } else {
                dataDirectory = new File(parentDirectory, DATA_DIRECTORY_PREFIX + instanceId);
            }

            EmbeddedPostgres embeddedPostgres = new EmbeddedPostgres(instanceId, postgresInstallDirectory, dataDirectory,
                    removeDataOnShutdown, serverConfiguration, localeConfiguration, connectionProperties,
                    port, errRedirector, outRedirector,
                    serverStartupWait);

            embeddedPostgres.cleanOldDataDirectories(parentDirectory);

            embeddedPostgres.boot();

            return embeddedPostgres;
        }
    }
}
