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
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.DELETE_ON_CLOSE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import de.softwareforge.testing.postgres.embedded.ProcessOutputLogger.StreamCapture;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages an embedded PostgreSQL server instance.
 */
public final class EmbeddedPostgres implements AutoCloseable {

    /**
     * The version of postgres used if no specific version has been given.
     */
    public static final String DEFAULT_POSTGRES_VERSION = "15";

    static final String[] LOCALHOST_SERVER_NAMES = {"localhost"};

    private static final String PG_TEMPLATE_DB = "template1";

    @VisibleForTesting
    static final Duration DEFAULT_PG_STARTUP_WAIT = Duration.ofSeconds(10);

    // folders need to be at least 10 minutes old to be considered for deletion.
    private static final long MINIMUM_AGE_IN_MS = Duration.ofMinutes(10).toMillis();

    // prefix for data folders in the parent that might be deleted
    private static final String DATA_DIRECTORY_PREFIX = "data-";

    private static final String PG_STOP_MODE = "fast";
    private static final String PG_STOP_WAIT_SECONDS = "5";
    static final String LOCK_FILE_NAME = "epg-lock";

    @SuppressWarnings("PMD.ProperLogger")
    private final Logger logger;

    private final String instanceId;
    private final File postgresInstallDirectory;
    private final File dataDirectory;

    private final Duration serverStartupWait;
    private final int port;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final ImmutableMap<String, String> serverConfiguration;
    private final ImmutableMap<String, String> localeConfiguration;
    private final ImmutableMap<String, String> connectionProperties;

    private final File lockFile;
    private volatile FileChannel lockChannel;
    private volatile FileLock lock;

    private final boolean removeDataOnShutdown;

    private final ProcessBuilder.Redirect errorRedirector;
    private final ProcessBuilder.Redirect outputRedirector;
    private final ProcessOutputLogger pgServerLogger;


    /**
     * Returns an instance that has been started and configured. The {@link Builder#withDefaults()} configuration has been applied.
     */
    @Nonnull
    public static EmbeddedPostgres defaultInstance() throws IOException {
        return builderWithDefaults().build();
    }

    /**
     * Returns a builder with default {@link Builder#withDefaults()} configuration already applied.
     */
    @Nonnull
    public static EmbeddedPostgres.Builder builderWithDefaults() {
        return new Builder().withDefaults();
    }

    /**
     * This returns an {@link EmbeddedPostgres} instance that can be solely used for version checking. It has not been booted and will not work for any other
     * things but executing {@link EmbeddedPostgres#getPostgresVersion()}. This is a performance optimization for code that needs to do version checking and
     * does not want to pay the penalty of spinning up and shutting down an instance.
     *
     * @return An unstarted {@link EmbeddedPostgres} instance.
     * @throws IOException Could not create the instance.
     * @since 4.1
     */
    public static EmbeddedPostgres forVersionCheck() throws IOException {
        return new Builder(false).build();
    }

    /**
     * Returns a new {@link Builder}.
     */
    @Nonnull
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
        this.pgServerLogger = new ProcessOutputLogger(logger);

        this.postgresInstallDirectory = checkNotNull(postgresInstallDirectory, "postgresInstallDirectory is null");
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

        logger.debug(format("data dir is %s, install dir is %s", this.dataDirectory, this.postgresInstallDirectory));
    }

    /**
     * Creates a {@link DataSource} object that connects to the standard system database.
     * <p>
     * The standard system database is the <code>template1</code> database.
     * <p>
     * Any modification to this database will be propagated to any new database that is created with <code>CREATE DATABASE...</code> unless another database is
     * explicitly named as the template..
     */
    @Nonnull
    public DataSource createTemplateDataSource() throws SQLException {
        checkState(started.get(), "instance has not been started!");

        return createDataSource(PG_DEFAULT_USER, PG_TEMPLATE_DB, getPort(), getConnectionProperties());
    }

    /**
     * Creates a {@link DataSource} object that connects to the default database.
     * <p>
     * The default database is the <code>postgres</code> database.
     */
    @Nonnull
    public DataSource createDefaultDataSource() throws SQLException {
        checkState(started.get(), "instance has not been started!");

        return createDataSource(PG_DEFAULT_USER, PG_DEFAULT_DB, getPort(), getConnectionProperties());
    }

    /**
     * Creates a {@link DataSource} with a specific user and database name.
     * <p>
     * Creating the DataSource does <b>not</b> create the database or the user itself. This must be done by the calling code (e.g. with a
     * {@link EmbeddedPostgresPreparer}).
     */
    @Nonnull
    public DataSource createDataSource(@Nonnull String user, @Nonnull String databaseName) throws SQLException {
        checkState(started.get(), "instance has not been started!");

        return createDataSource(user, databaseName, getPort(), getConnectionProperties());
    }

    static DataSource createDataSource(String user, String databaseName, int port, Map<String, String> connectionProperties) throws SQLException {
        checkNotNull(user, "user is null");
        checkNotNull(databaseName, "databaseName is null");
        checkNotNull(connectionProperties, "connectionProperties is null");

        final PGSimpleDataSource ds = new PGSimpleDataSource();

        ds.setServerNames(LOCALHOST_SERVER_NAMES);
        ds.setPortNumbers(new int[]{port});
        ds.setDatabaseName(databaseName);
        ds.setUser(user);

        for (final Entry<String, String> entry : connectionProperties.entrySet()) {
            ds.setProperty(entry.getKey(), entry.getValue());
        }

        return ds;
    }

    /**
     * Returns the network (TCP) port for the PostgreSQL server instance.
     */
    public int getPort() {
        checkState(started.get(), "instance has not been started!");

        return port;
    }

    /**
     * Returns the connection properties for the PostgreSQL server instance.
     */
    @Nonnull
    ImmutableMap<String, String> getConnectionProperties() {
        checkState(started.get(), "instance has not been started!");

        return connectionProperties;
    }

    /**
     * Returns the instance id for the PostgreSQL server instance. This id is an alphanumeric string that can be used to differentiate between multiple embedded
     * PostgreSQL server instances.
     */
    @Nonnull
    public String instanceId() {
        checkState(started.get(), "instance has not been started!");

        return instanceId;
    }

    /**
     * Return the version of the PostgreSQL installation that is used by this instance.
     *
     * @return A string representing the Postgres version as described in the <a href="https://www.postgresql.org/support/versioning/">Postgres versioning
     * policy</a>.
     * @since 4.1
     */
    public String getPostgresVersion() throws IOException {

        StringBuilder sb = new StringBuilder();
        StreamCapture logCapture = pgServerLogger.captureStreamAsConsumer(sb::append);

        List<String> commandAndArgs = ImmutableList.of(pgBin("pg_ctl"), "--version");
        final Stopwatch watch = system(commandAndArgs, logCapture);

        String version = "unknown";

        try {
            logCapture.getCompletion().get();
            final String s = sb.toString();
            checkState(s.startsWith("pg_ctl "), "Response %s does not match 'pg_ctl'", sb);
            version = s.substring(s.lastIndexOf(' ')).trim();

        } catch (ExecutionException e) {
            throw new IOException(format("Process '%s' failed%n", Joiner.on(" ").join(commandAndArgs)), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.debug(format("postgres version check completed in %s", formatDuration(watch.elapsed())));
        return version;
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
    DatabaseInfo createDefaultDatabaseInfo() {
        return DatabaseInfo.builder().port(getPort()).connectionProperties(getConnectionProperties()).build();
    }


    private void boot() throws IOException {
        EmbeddedUtil.ensureDirectory(this.dataDirectory);

        if (this.removeDataOnShutdown || !new File(this.dataDirectory, "postgresql.conf").exists()) {
            initDatabase();
        }

        lock();

        startDatabase();
    }


    private synchronized void lock() throws IOException {
        this.lockChannel = FileChannel.open(this.lockFile.toPath(), CREATE, WRITE, TRUNCATE_EXISTING);
        this.lock = this.lockChannel.tryLock();
        checkState(lock != null, "could not lock %s", lockFile);
    }

    private synchronized void unlock() throws IOException {
        if (lock != null) {
            lock.release();
        }
        Closeables.close(this.lockChannel, true);
        Files.deleteIfExists(this.lockFile.toPath());
    }

    private void initDatabase() throws IOException {
        ImmutableList.Builder<String> commandBuilder = ImmutableList.builder();
        commandBuilder.add(pgBin("initdb"))
                .addAll(createInitDbOptions())
                .add("-A", "trust",
                        "-U", PG_DEFAULT_USER,
                        "-D", this.dataDirectory.getPath(),
                        "-E", "UTF-8");
        final Stopwatch watch = system(commandBuilder.build(), pgServerLogger.captureStreamAsLog());
        logger.debug(format("initdb completed in %s", formatDuration(watch.elapsed())));
    }

    private void startDatabase() throws IOException {
        checkState(!started.getAndSet(true), "database already started!");

        final ImmutableList.Builder<String> commandBuilder = ImmutableList.builder();
        commandBuilder.add(pgBin("pg_ctl"),
                "-D", this.dataDirectory.getPath(),
                "-o", String.join(" ", createInitOptions()),
                "start"
        );

        final Stopwatch watch = Stopwatch.createStarted();
        final Process postmaster = spawn("pg", commandBuilder.build(), pgServerLogger.captureStreamAsLog());

        logger.info(format("started as pid %d on port %d", postmaster.pid(), port));
        logger.debug(format("Waiting up to %s for server startup to finish", formatDuration(serverStartupWait)));

        Runtime.getRuntime().addShutdownHook(newCloserThread());

        checkState(waitForServerStartup(), "Could not start PostgreSQL server, interrupted?");
        logger.debug(format("startup complete in %s", formatDuration(watch.elapsed())));
    }

    private void stopDatabase(File dataDirectory) throws IOException {
        if (started.get()) {
            final ImmutableList.Builder<String> commandBuilder = ImmutableList.builder();
            commandBuilder.add(pgBin("pg_ctl"),
                    "-D", dataDirectory.getPath(),
                    "stop",
                    "-m", PG_STOP_MODE,
                    "-t", PG_STOP_WAIT_SECONDS, "-w");

            final Stopwatch watch = system(commandBuilder.build(), pgServerLogger.captureStreamAsLog());
            logger.debug(format("shutdown complete in %s", formatDuration(watch.elapsed())));
        }
        pgServerLogger.close();
    }

    private List<String> createInitOptions() {
        final ImmutableList.Builder<String> initOptions = ImmutableList.builder();
        initOptions.add(
                "-p", Integer.toString(port),
                "-F");

        serverConfiguration.forEach((key, value) -> {
            initOptions.add("-c");
            if (!value.isEmpty()) {
                initOptions.add(key + "=" + value);
            } else {
                initOptions.add(key + "=true");
            }
        });

        return initOptions.build();
    }

    @VisibleForTesting
    List<String> createInitDbOptions() {
        final ImmutableList.Builder<String> localeOptions = ImmutableList.builder();

        localeConfiguration.forEach((key, value) -> {
            if (!value.isEmpty()) {
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

    @SuppressWarnings("PMD.CheckResultSet") // see https://github.com/pmd/pmd/issues/5209 / https://github.com/pmd/pmd/issues/5031
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
                this.close();
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

            try (FileChannel fileChannel = FileChannel.open(lockFile.toPath(), CREATE, WRITE, TRUNCATE_EXISTING, DELETE_ON_CLOSE);
                    FileLock lock = fileChannel.tryLock()) {
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
            } catch (final IOException e) {
                logger.warn("while cleaning old data directories:", e);
            }
        }
    }

    private String pgBin(String binaryName) {
        final String extension = EmbeddedUtil.IS_OS_WINDOWS ? ".exe" : "";
        return new File(this.postgresInstallDirectory, "bin/" + binaryName + extension).getPath();
    }

    private Process spawn(@Nullable String processName, List<String> commandAndArgs,
            StreamCapture logCapture)
            throws IOException {
        final ProcessBuilder builder = new ProcessBuilder(commandAndArgs);
        builder.redirectErrorStream(true);
        builder.redirectError(errorRedirector);
        builder.redirectOutput(outputRedirector);
        final Process process = builder.start();

        if (outputRedirector == Redirect.PIPE) {
            processName = processName != null ? processName : process.info().command().map(EmbeddedUtil::getFileBaseName).orElse("<unknown>");
            String name = format("%s (%d)", processName, process.pid());
            logCapture.accept(name, process.getInputStream());
        }
        return process;
    }

    private Stopwatch system(List<String> commandAndArgs, StreamCapture logCapture) throws IOException {
        checkArgument(!commandAndArgs.isEmpty(), "No commandAndArgs given!");
        String prefix = EmbeddedUtil.getFileBaseName(commandAndArgs.get(0));

        Stopwatch watch = Stopwatch.createStarted();
        try {
            Process process = spawn(prefix, commandAndArgs, logCapture);
            if (process.waitFor() != 0) {
                if (errorRedirector == Redirect.PIPE) {
                    try (InputStreamReader errorReader = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
                        throw new IOException(format("Process '%s' failed%n%s", Joiner.on(" ").join(commandAndArgs), CharStreams.toString(errorReader)));
                    }
                } else {
                    throw new IOException(format("Process '%s' failed",
                            Joiner.on(" ").join(commandAndArgs)));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return watch;
    }

    /**
     * Creates a new {@link EmbeddedPostgres} instance and starts it.
     */
    public static class Builder {

        private File installationBaseDirectory = null;
        private File dataDirectory = null;

        private final Map<String, String> serverConfiguration = new HashMap<>();
        private final Map<String, String> localeConfiguration = new HashMap<>();
        private boolean removeDataOnShutdown = true;
        private int port = 0;
        private String serverVersion = DEFAULT_POSTGRES_VERSION;
        private final Map<String, String> connectionProperties = new HashMap<>();
        private NativeBinaryManager nativeBinaryManager = null;
        private Duration serverStartupWait = DEFAULT_PG_STARTUP_WAIT;

        private ProcessBuilder.Redirect errorRedirector = ProcessBuilder.Redirect.PIPE;
        private ProcessBuilder.Redirect outputRedirector = ProcessBuilder.Redirect.PIPE;

        private final boolean bootInstance;

        private Builder(boolean bootInstance) {
            this.bootInstance = bootInstance;
        }

        Builder() {
            this(true);
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
        @Nonnull
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
        @Nonnull
        public Builder setServerStartupWait(@Nonnull Duration serverStartupWait) {
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
        @Nonnull
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
        @Nonnull
        public Builder setDataDirectory(@Nonnull Path dataDirectory) {
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
        @Nonnull
        public Builder setDataDirectory(@Nonnull String dataDirectory) {
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
        @Nonnull
        public Builder setDataDirectory(@Nonnull File dataDirectory) {
            this.dataDirectory = checkNotNull(dataDirectory, "dataDirectory is null");
            return this;
        }

        /**
         * Adds a server configuration parameter. All parameters are passed to the PostgreSQL server a startup using the <code>postgres</code> command.
         * <p>
         * Values and their function are specific to the PostgreSQL version selected.
         * <p>
         * See <a href="https://www.postgresql.org/docs/13/runtime-config.html">the PostgreSQL runtime configuration</a> for more information.
         *
         * @param key   Configuration parameter name. Must not be null.
         * @param value Configuration parameter value. Must not be null.
         * @return The builder itself.
         */
        @Nonnull
        public Builder addServerConfiguration(@Nonnull String key, @Nonnull String value) {
            checkNotNull(key, "key is null");
            checkNotNull(value, "value is null");
            this.serverConfiguration.put(key, value);
            return this;
        }

        /**
         * Adds a configuration parameters for the <code>initdb</code> command. The <code>initdb</code> command is used to create the PostgreSQL server.
         * <p>
         * Each value is added as a command line parameter to the command.
         * <p>
         * See the <a href="https://www.postgresql.org/docs/13/app-initdb.html">PostgreSQL initdb documentation</a> for an overview of possible values.
         *
         * @param key   initdb parameter name. Must not be null.
         * @param value initdb parameter value. Must not be null. When the empty string is used as the value, the resulting command line parameter will not have
         *              a equal sign and a value assigned.
         * @return The builder itself.
         * @since 3.0
         */
        @Nonnull
        public Builder addInitDbConfiguration(@Nonnull String key, @Nonnull String value) {
            checkNotNull(key, "key is null");
            checkNotNull(value, "value is null");
            this.localeConfiguration.put(key, value);
            return this;
        }

        /**
         * Adds a connection property. These properties are set on every connection handed out by the data source. See
         * <a href="https://jdbc.postgresql.org/documentation/head/connect.html#connection-parameters">the
         * PostgreSQL JDBC driver documentation</a> for possible values.
         *
         * @param key   connection property name. Must not be null.
         * @param value connection property value. Must not be null.
         * @return The builder itself.
         */
        @Nonnull
        public Builder addConnectionProperty(@Nonnull String key, @Nonnull String value) {
            checkNotNull(key, "key is null");
            checkNotNull(value, "value is null");
            this.connectionProperties.put(key, value);
            return this;
        }

        /**
         * Sets the directory where the PostgreSQL distribution is unpacked. Setting the installation base directory resets the {@link NativeBinaryManager} used
         * to locate the postgres installation back to the default (which is to download the zonky.io Postgres archive and unpack it in the installation
         * directory. The default is using a managed directory.
         *
         * @param installationBaseDirectory The directory to unpack the postgres distribution. The current user must be able to create and write this directory.
         *                                  Must not be null.
         * @return The builder itself.
         * @since 3.0
         */
        @Nonnull
        public Builder setInstallationBaseDirectory(@Nonnull File installationBaseDirectory) {
            checkNotNull(installationBaseDirectory, "installationBaseDirectory is null");
            this.installationBaseDirectory = installationBaseDirectory;
            this.nativeBinaryManager = null;
            return this;
        }

        /**
         * Explicitly set the TCP port for the PostgreSQL server. If the port is not available, starting the server will fail. Default is to find and use an
         * available TCP port.
         *
         * @param port The port to use. Must be &gt; 1023 and &lt; 65536.
         * @return The builder itself.
         */
        @Nonnull
        public Builder setPort(int port) {
            checkState(port > 1_023 && port < 65_535, "Port %s is not within 1024..65535", port);
            this.port = port;
            return this;
        }

        /**
         * Set the version of the PostgreSQL server. This value is passed to the default binary manager which will try to resolve this version from existing
         * Maven artifacts. The value is ignored if {@link Builder#setNativeBinaryManager(NativeBinaryManager)} is called.
         * <p>
         * Not every PostgreSQL version is supported by pg-embedded. Some older versions lack the necessary options for the command line parameters and will
         * fail at startup. Currently, every version 10 or newer should be working.
         *
         * @param serverVersion A partial or full version. Valid values are e.g. "12" or "11.3".
         * @return The builder itself.
         * @since 3.0
         */
        @Nonnull
        public Builder setServerVersion(@Nonnull String serverVersion) {
            this.serverVersion = checkNotNull(serverVersion, "serverVersion is null");

            return this;
        }

        /**
         * Set a {@link ProcessBuilder.Redirect} instance to receive stderr output from the spawned processes.
         *
         * @param errorRedirector a {@link ProcessBuilder.Redirect} instance. Must not be null.
         * @return The builder itself.
         */
        @Nonnull
        public Builder setErrorRedirector(@Nonnull ProcessBuilder.Redirect errorRedirector) {
            this.errorRedirector = checkNotNull(errorRedirector, "errorRedirector is null");
            return this;
        }

        /**
         * Set a {@link ProcessBuilder.Redirect} instance to receive stdout output from the spawned processes.
         *
         * @param outputRedirector a {@link ProcessBuilder.Redirect} instance. Must not be null.
         * @return The builder itself.
         */
        @Nonnull
        public Builder setOutputRedirector(@Nonnull ProcessBuilder.Redirect outputRedirector) {
            this.outputRedirector = checkNotNull(outputRedirector, "outputRedirector is null");
            return this;
        }

        /**
         * Sets the {@link NativeBinaryManager} that provides the location of the postgres installation. Explicitly setting a binary manager overrides the
         * installation base directory location set with {@link Builder#setInstallationBaseDirectory(File)} as this is only used by the default binary manager.
         * Calling {@link Builder#setInstallationBaseDirectory(File)} after this method undoes setting the binary manager.
         *
         * @param nativeBinaryManager A {@link NativeBinaryManager} implementation. Must not be null.
         * @return The builder itself.
         * @since 3.0
         */
        @Nonnull
        public Builder setNativeBinaryManager(@Nonnull NativeBinaryManager nativeBinaryManager) {
            this.nativeBinaryManager = checkNotNull(nativeBinaryManager, "nativeBinaryManager is null");
            return this;
        }

        /**
         * Use a locally installed PostgreSQL server for tests. The tests will still spin up a new instance and locate the data in the data directory but it
         * will use the locally installed binaries for starting and stopping. Calling this method sets a binary manager, so it overrides
         * {@link Builder#setNativeBinaryManager(NativeBinaryManager)}. Calling this method makes the builder ignore the
         * {@link Builder#setInstallationBaseDirectory(File)} setting.
         *
         * @param directory A local directory that contains a standard PostgreSQL installation. The directory must exist and read and executable.
         * @return The builder itself.
         * @since 3.0
         */
        @Nonnull
        public Builder useLocalPostgresInstallation(@Nonnull File directory) {
            checkNotNull(directory, "directory is null");
            checkState(directory.exists() && directory.isDirectory(), "'%s' either does not exist or is not a directory!", directory);
            return setNativeBinaryManager(() -> directory);
        }

        /**
         * Creates and boots a new {@link EmbeddedPostgres} instance.
         *
         * @return A {@link EmbeddedPostgres} instance representing a started PostgreSQL server.
         * @throws IOException If the server could not be installed or started.
         */
        @Nonnull
        public EmbeddedPostgres build() throws IOException {
            // Builder Id
            final String instanceId = EmbeddedUtil.randomAlphaNumeric(16);

            int port = this.port != 0 ? this.port : EmbeddedUtil.allocatePort();

            // installation root if nothing has been set by the user.
            final File parentDirectory = EmbeddedUtil.getWorkingDirectory();

            NativeBinaryManager nativeBinaryManager = this.nativeBinaryManager;
            if (nativeBinaryManager == null) {
                final String serverVersion = System.getProperty("pg-embedded.postgres-version", this.serverVersion);
                nativeBinaryManager = new TarXzCompressedBinaryManager(new ZonkyIOPostgresLocator(serverVersion));
            }

            // Use the parent directory if no installation directory set.
            File installationBaseDirectory = Objects.requireNonNullElse(this.installationBaseDirectory, parentDirectory);
            nativeBinaryManager.setInstallationBaseDirectory(installationBaseDirectory);

            // this is where the binary manager actually places the unpackaged postgres installation.
            final File postgresInstallDirectory = nativeBinaryManager.getLocation();

            File dataDirectory = this.dataDirectory;
            if (dataDirectory == null) {
                dataDirectory = new File(parentDirectory, DATA_DIRECTORY_PREFIX + instanceId);
            }

            EmbeddedPostgres embeddedPostgres = new EmbeddedPostgres(instanceId, postgresInstallDirectory, dataDirectory,
                    removeDataOnShutdown, serverConfiguration, localeConfiguration, connectionProperties,
                    port, errorRedirector, outputRedirector,
                    serverStartupWait);

            embeddedPostgres.cleanOldDataDirectories(parentDirectory);

            // for version checking (calling getPostgresVersion(), the instance does not need to run
            // this is a special case to make the version check run faster for unit test selection.
            if (bootInstance) {
                embeddedPostgres.boot();
            }

            return embeddedPostgres;
        }
    }
}
