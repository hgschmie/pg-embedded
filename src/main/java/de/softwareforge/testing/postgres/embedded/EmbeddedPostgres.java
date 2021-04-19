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

import java.io.Closeable;
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
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static de.softwareforge.testing.postgres.embedded.EmbeddedUtil.formatDuration;
import static java.lang.String.format;

public final class EmbeddedPostgres implements Closeable {

    static final String[] LOCALHOST_SERVERNAMES = new String[]{"localhost"};
    static final String JDBC_FORMAT = "jdbc:postgresql://localhost:%d/%s?user=%s";

    // default user used for databases
    public static final String PG_DEFAULT_USER = "postgres";

    @VisibleForTesting
    public static final Duration DEFAULT_PG_STARTUP_WAIT = Duration.ofSeconds(10);

    // folders need to be at least 10 minutes old to be considered for deletion.
    private static final long MINIMUM_AGE_IN_MS = Duration.ofMinutes(10).toMillis();
    // prefix for data folders in the parent that might be deleted
    private static final String DATA_DIRECTORY_PREFIX = "epd-";

    private static final String PG_STOP_MODE = "fast";
    private static final String PG_STOP_WAIT_S = "5";
    private static final String PG_TEMPLATE_DB = "template1";
    static final String LOCK_FILE_NAME = "epg-lock";

    private final Logger logger;

    private final String instanceId;
    private final File pgDir;
    private final File dataDirectory;

    private final Duration pgStartupWait;
    private final int port;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final ImmutableMap<String, String> postgresConfig;
    private final ImmutableMap<String, String> localeConfig;
    private final ImmutableMap<String, String> connectConfig;

    private final File lockFile;
    private volatile FileOutputStream lockStream;
    private volatile FileLock lock;

    private final boolean cleanDataDirectory;

    private final ProcessBuilder.Redirect errorRedirector;
    private final ProcessBuilder.Redirect outputRedirector;

    private EmbeddedPostgres(
            final String instanceId,
            final File postgresInstallDirectory,
            final File dataDirectory,
            final boolean cleanDataDirectory,
            final Map<String, String> postgresConfig,
            final Map<String, String> localeConfig,
            final Map<String, String> connectConfig,
            final int port,
            final ProcessBuilder.Redirect errorRedirector,
            final ProcessBuilder.Redirect outputRedirector,
            final Duration pgStartupWait) {

        this.instanceId = checkNotNull(instanceId, "instanceId is null");

        this.logger = LoggerFactory.getLogger(toString());

        this.pgDir = checkNotNull(postgresInstallDirectory, "postgresInstallDirectory is null");
        this.dataDirectory = checkNotNull(dataDirectory, "dataDirectory is null");

        this.cleanDataDirectory = cleanDataDirectory;

        this.postgresConfig = ImmutableMap.copyOf(checkNotNull(postgresConfig, "postgresConfig is null"));
        this.localeConfig = ImmutableMap.copyOf(checkNotNull(localeConfig, "localeConfig is null"));
        this.connectConfig = ImmutableMap.copyOf(checkNotNull(connectConfig, "connectConfig is null"));

        this.port = port;

        this.errorRedirector = checkNotNull(errorRedirector, "errorRedirector is null");
        this.outputRedirector = checkNotNull(outputRedirector, "outputRedirector is null");

        this.pgStartupWait = checkNotNull(pgStartupWait, "pgStartupWait is null");
        this.lockFile = new File(this.dataDirectory, LOCK_FILE_NAME);

        logger.debug(format("data dir is %s, install dir is %s", this.dataDirectory, this.pgDir));
    }

    public DataSource getTemplateDatabase() throws SQLException {
        return getDatabase(PG_DEFAULT_USER, PG_TEMPLATE_DB);
    }

    public DataSource getTemplateDatabase(Map<String, String> properties) throws SQLException {
        return getDatabase(PG_DEFAULT_USER, PG_TEMPLATE_DB, properties);
    }

    public DataSource getPostgresDatabase() throws SQLException {
        return getDatabase(PG_DEFAULT_USER, PG_DEFAULT_USER);
    }

    public DataSource getPostgresDatabase(Map<String, String> properties) throws SQLException {
        return getDatabase(PG_DEFAULT_USER, PG_DEFAULT_USER, properties);
    }

    public DataSource getDatabase(String user, String databaseName) throws SQLException {
        return getDatabase(user, databaseName, this.connectConfig);
    }

    public DataSource getDatabase(String user, String databaseName, Map<String, String> properties) throws SQLException {
        checkNotNull(user, "user is null");
        checkNotNull(databaseName, "databaseName is null");
        checkNotNull(properties, "properties is null");

        final PGSimpleDataSource ds = new PGSimpleDataSource();

        ds.setServerNames(LOCALHOST_SERVERNAMES);
        ds.setPortNumbers(new int[]{port});
        ds.setDatabaseName(databaseName);
        ds.setUser(user);

        for (final Entry<String, String> entry : properties.entrySet()) {
            ds.setProperty(entry.getKey(), entry.getValue());
        }

        return ds;
    }

    public String getJdbcUrl(String user, String databaseName) {
        checkNotNull(user, "user is null");
        checkNotNull(databaseName, "databaseName is null");

        return format(JDBC_FORMAT, port, databaseName, user);
    }

    public int getPort() {
        return port;
    }

    ImmutableMap<String, String> getConnectConfig() {
        return connectConfig;
    }

    // internal methods


    private void boot() throws IOException {
        EmbeddedUtil.mkdirs(this.dataDirectory);

        if (this.cleanDataDirectory || !new File(this.dataDirectory, "postgresql.conf").exists()) {
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
        logger.info(format("initdb completed in %s", formatDuration(watch.elapsed())));
    }

    private void startDatabase() throws IOException {
        checkState(!started.getAndSet(true), "pg already started!");

        final ImmutableList.Builder<String> commandBuilder = ImmutableList.builder();
        commandBuilder.add(pgBin("pg_ctl"),
                "-D", this.dataDirectory.getPath(),
                "-o", createInitOptions().stream().collect(Collectors.joining(" ")),
                "start"
        );

        final Stopwatch watch = Stopwatch.createStarted();
        final Process postmaster = spawn("pg", commandBuilder.build());

        logger.info(format("started (pid %d) on port %d. Waiting up to %s for server startup to finish", postmaster.pid(), port,
                formatDuration(pgStartupWait)));

        Runtime.getRuntime().addShutdownHook(newCloserThread());

        checkState(waitForServerStartup(), "Could not start pg, interrupted?");
        logger.info(format("startup complete in %s", formatDuration(watch.elapsed())));
    }

    private void stopDatabase(File dataDirectory) throws IOException {
        final ImmutableList.Builder<String> commandBuilder = ImmutableList.builder();
        commandBuilder.add(pgBin("pg_ctl"),
                "-D", dataDirectory.getPath(),
                "stop",
                "-m", PG_STOP_MODE,
                "-t", PG_STOP_WAIT_S, "-w");

        final Stopwatch watch = system(commandBuilder.build());
        logger.info(format("shutdown complete in %s", formatDuration(watch.elapsed())));
    }

    private List<String> createInitOptions() {
        final ImmutableList.Builder<String> initOptions = ImmutableList.builder();
        initOptions.add(
                "-p", Integer.toString(port),
                "-F");

        postgresConfig.forEach((k, v) -> {
            initOptions.add("-c");
            initOptions.add(k + "=" + v);
        });

        return initOptions.build();
    }

    private List<String> createLocaleOptions() {
        final ImmutableList.Builder<String> localeOptions = ImmutableList.builder();

        localeConfig.forEach((key, value) -> {
            if (SystemUtils.IS_OS_WINDOWS) {
                localeOptions.add("--" + key + "=" + value);
            } else {
                localeOptions.add("--" + key, value);
            }
        });
        return localeOptions.build();
    }

    private boolean waitForServerStartup() throws IOException {
        Throwable lastCause = null;
        final long start = System.nanoTime();
        final long maxWaitNs = TimeUnit.NANOSECONDS.convert(pgStartupWait.toMillis(), TimeUnit.MILLISECONDS);
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
        throw new IOException("Gave up waiting for server to start after " + pgStartupWait.toMillis() + "ms", lastCause);
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
        try (Connection c = getPostgresDatabase().getConnection();
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

        if (cleanDataDirectory) {
            try {
                EmbeddedUtil.rmdirs(dataDirectory);
            } catch (Exception e) {
                logger.error(format("Could not clean up directory %s:", dataDirectory.getAbsolutePath()), e);
            }
        } else {
            logger.info(format("preserved data directory %s", dataDirectory.getAbsolutePath()));
        }
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
            // the exists b/c non-existant files return 0 (epoch) as lastModified so
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
                            logger.debug("shut down orphaned pg!");
                        } catch (Exception e) {
                            logger.warn(format("failed to stop pg in %s:", dir), e);
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
        final String extension = SystemUtils.IS_OS_WINDOWS ? ".exe" : "";
        return new File(this.pgDir, "bin/" + binaryName + extension).getPath();
    }


    /**
     * Returns a single instance that has been started and configured. The {@link Builder#withDefaults()} configuration has been applied.
     *
     * @return configured instance.
     * @throws IOException If the instance could not be built.
     */
    public static EmbeddedPostgres defaultInstance() throws IOException {
        return builderWithDefaults().build();
    }

    public static EmbeddedPostgres.Builder builderWithDefaults() {
        return new Builder().withDefaults();
    }

    public static EmbeddedPostgres.Builder builder() {
        return new Builder();
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

    @Override
    public String toString() {
        checkNotNull(this.instanceId, "instanceId is null");
        return this.getClass().getName() + '$' + this.instanceId;
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


    public static class Builder {

        private File installationDirectory = null;
        private File dataDirectory = null;

        private final Map<String, String> config = new HashMap<>();
        private final Map<String, String> localeConfig = new HashMap<>();
        private boolean cleanDataDirectory = true;
        private int port = 0;
        private final Map<String, String> connectConfig = new HashMap<>();
        private PgDirectoryResolver pgDirectoryResolver = UncompressBundleDirectoryResolver.getDefault();
        private Duration pgStartupWait = DEFAULT_PG_STARTUP_WAIT;

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
            config.put("timezone", "UTC");
            config.put("synchronous_commit", "off");
            config.put("max_connections", "300");
            return this;
        }

        public Builder setPGStartupWait(Duration pgStartupWait) {
            checkNotNull(pgStartupWait, "pgStartupWait is null");
            checkArgument(!pgStartupWait.isNegative(), "Negative durations are not permitted.");

            this.pgStartupWait = pgStartupWait;
            return this;
        }

        public Builder setCleanDataDirectory(boolean cleanDataDirectory) {
            this.cleanDataDirectory = cleanDataDirectory;
            return this;
        }

        public Builder setDataDirectory(Path dataDirectory) {
            checkNotNull(dataDirectory, "dataDirectory is null");
            return setDataDirectory(dataDirectory.toFile());
        }

        public Builder setDataDirectory(String dataDirectory) {
            checkNotNull(dataDirectory, "dataDirectory is null");
            return setDataDirectory(new File(dataDirectory));
        }

        public Builder setDataDirectory(File dataDirectory) {
            this.dataDirectory = checkNotNull(dataDirectory, "dataDirectory is null");
            return this;
        }

        public Builder setServerConfig(String key, String value) {
            checkNotNull(key, "key is null");
            checkNotNull(value, "value is null");
            this.config.put(key, value);
            return this;
        }

        public Builder setLocaleConfig(String key, String value) {
            checkNotNull(key, "key is null");
            checkNotNull(value, "value is null");
            this.localeConfig.put(key, value);
            return this;
        }

        public Builder setConnectConfig(String key, String value) {
            checkNotNull(key, "key is null");
            checkNotNull(value, "value is null");
            this.connectConfig.put(key, value);
            return this;
        }

        public Builder setInstallationDirectory(File installationDirectory) {
            checkNotNull(installationDirectory, "workingDirectory is null");
            this.installationDirectory = installationDirectory;
            return this;
        }

        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        public Builder setErrorRedirector(ProcessBuilder.Redirect errRedirector) {
            this.errRedirector = checkNotNull(errRedirector, "errRedirector is null");
            return this;
        }

        public Builder setOutputRedirector(ProcessBuilder.Redirect outRedirector) {
            this.outRedirector = checkNotNull(outRedirector, "outRedirector is null");
            return this;
        }

        public Builder setPgDirectoryResolver(PgDirectoryResolver pgDirectoryResolver) {
            this.pgDirectoryResolver = checkNotNull(pgDirectoryResolver, "pgDirectoryResolver is null");
            return this;
        }

        public Builder setPostgresBinaryDirectory(File directory) {
            checkNotNull(directory, "directory is null");

            return setPgDirectoryResolver((x) -> directory);
        }

        public EmbeddedPostgres build() throws IOException {
            // Builder Id
            final String instanceId = RandomStringUtils.randomAlphanumeric(16);

            // installation root if nothing has been set by the user.
            final File parentDirectory = EmbeddedUtil.getWorkingDirectory();

            int port = this.port != 0 ? this.port : EmbeddedUtil.allocatePort();

            final File installationDirectory = MoreObjects.firstNonNull(this.installationDirectory, parentDirectory);

            final File postgresInstallDirectory = pgDirectoryResolver.getDirectory(installationDirectory);
            final File dataDirectory;

            EmbeddedUtil.mkdirs(parentDirectory);
            if (this.dataDirectory != null) {
                dataDirectory = this.dataDirectory;
            } else {
                dataDirectory = new File(parentDirectory, DATA_DIRECTORY_PREFIX + instanceId);
            }

            EmbeddedPostgres embeddedPostgres = new EmbeddedPostgres(instanceId, postgresInstallDirectory, dataDirectory, cleanDataDirectory, config,
                    localeConfig, connectConfig, port, errRedirector, outRedirector,
                    pgStartupWait);

            embeddedPostgres.cleanOldDataDirectories(parentDirectory);

            embeddedPostgres.boot();

            return embeddedPostgres;
        }

        // equals and hashcode are needed for the PreparedDb extension to correctly identify existing preparers.

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Builder builder = (Builder) o;
            return cleanDataDirectory == builder.cleanDataDirectory && port == builder.port && Objects
                    .equals(installationDirectory, builder.installationDirectory) && Objects.equals(dataDirectory, builder.dataDirectory) && config
                    .equals(builder.config) && localeConfig.equals(builder.localeConfig) && connectConfig.equals(builder.connectConfig) && pgDirectoryResolver
                    .equals(builder.pgDirectoryResolver) && pgStartupWait.equals(builder.pgStartupWait) && errRedirector.equals(builder.errRedirector)
                    && outRedirector
                    .equals(builder.outRedirector);
        }

        @Override
        public int hashCode() {
            return Objects.hash(installationDirectory, dataDirectory, config, localeConfig, cleanDataDirectory, port, connectConfig, pgDirectoryResolver,
                    pgStartupWait,
                    errRedirector, outRedirector);
        }
    }
}
