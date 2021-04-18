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
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.sql.DataSource;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

public class EmbeddedPostgres implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPostgres.class);

    static final String[] LOCALHOST_SERVERNAMES = new String[]{"localhost"};
    static final String JDBC_FORMAT = "jdbc:postgresql://localhost:%d/%s?user=%s";

    @VisibleForTesting
    public static final Duration DEFAULT_PG_STARTUP_WAIT = Duration.ofSeconds(10);
    // folders need to be at least 10 minutes old to be considered for deletion.
    private static final long MINIMUM_AGE_IN_MS = Duration.ofMinutes(10).toMillis();
    // prefix for data folders in the parent that might be deleted
    private static final String DATA_DIRECTORY_PREFIX = "epd-";

    private static final String PG_STOP_MODE = "fast";
    private static final String PG_STOP_WAIT_S = "5";
    private static final String PG_DEFAULT_USER = "postgres";
    private static final String PG_TEMPLATE_DB = "template1";
    static final String LOCK_FILE_NAME = "epg-lock";

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

        LOG.debug("{} postgres: data directory is {}, postgres directory is {}", instanceId, this.dataDirectory, this.pgDir);
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

        for (Entry<String, String> entry : properties.entrySet()) {
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
            initdb();
        }

        lock();

        startPostmaster();
    }


    private static int detectPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            while (!socket.isBound()) {
                Thread.sleep(50);
            }
            return socket.getLocalPort();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Thread interrupted!", e);
        }
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

    private void initdb() throws IOException {
        final StopWatch watch = new StopWatch();
        watch.start();
        ImmutableList.Builder<String> commandBuilder = ImmutableList.builder();
        commandBuilder.add(pgBin("initdb"))
                .addAll(createLocaleOptions())
                .add("-A", "trust",
                        "-U", PG_DEFAULT_USER,
                        "-D", this.dataDirectory.getPath(),
                        "-E", "UTF-8");
        system(commandBuilder.build());
        LOG.info("{} initdb completed in {}", instanceId, watch);
    }

    private void startPostmaster() throws IOException {
        checkState(!started.getAndSet(true), "Postmaster already started!");

        final StopWatch watch = new StopWatch();
        watch.start();

        final ImmutableList.Builder<String> commandBuilder = ImmutableList.builder();
        commandBuilder.add(pgBin("pg_ctl"),
                "-D", this.dataDirectory.getPath(),
                "-o", createInitOptions().stream().collect(Collectors.joining(" ")),
                "start"
        );

        Process postmaster = spawn("pg", commandBuilder.build());

        LOG.info("{} postmaster started as {} on port {}.  Waiting up to {} for server startup to finish.", instanceId, postmaster, port,
                pgStartupWait);

        Runtime.getRuntime().addShutdownHook(newCloserThread());

        waitForServerStartup(watch);
    }

    private void stopPostmaster(File dataDirectory) throws IOException {
        ImmutableList.Builder<String> commandBuilder = ImmutableList.builder();
        commandBuilder.add(pgBin("pg_ctl"),
                "-D", dataDirectory.getPath(),
                "stop",
                "-m", PG_STOP_MODE,
                "-t", PG_STOP_WAIT_S, "-w");
        system(commandBuilder.build());
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

    private void waitForServerStartup(StopWatch watch) throws IOException {
        Throwable lastCause = null;
        final long start = System.nanoTime();
        final long maxWaitNs = TimeUnit.NANOSECONDS.convert(pgStartupWait.toMillis(), TimeUnit.MILLISECONDS);
        while (System.nanoTime() - start < maxWaitNs) {
            try {
                if (verifyReady()) {
                    LOG.info("{} postmaster startup finished in {}", instanceId, watch);
                    return;
                }
            } catch (final SQLException e) {
                lastCause = e;
                LOG.trace("While waiting for server startup", e);
            }

            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
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
            } catch (IOException ex) {
                LOG.error("Unexpected IOException from Closeables.close", ex);
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
        final StopWatch watch = new StopWatch();
        watch.start();

        try {
            stopPostmaster(this.dataDirectory);
            LOG.info("{} shut down postmaster in {}", instanceId, watch);
        } catch (final Exception e) {
            LOG.error("Could not stop postmaster " + instanceId, e);
        }

        unlock();

        if (cleanDataDirectory) {
            try {
                FileUtils.deleteDirectory(dataDirectory);
            } catch (Exception e) {
                LOG.error("Could not clean up directory {}: e", dataDirectory.getAbsolutePath(), e);
            }
        } else {
            LOG.info("Preserved data directory {}", dataDirectory.getAbsolutePath());
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
                    LOG.info("Found stale data directory {}", dir);
                    if (new File(dir, "postmaster.pid").exists()) {
                        try {
                            stopPostmaster(dir);
                            LOG.info("Shut down orphaned postmaster!");
                        } catch (Exception e) {
                            if (LOG.isDebugEnabled()) {
                                LOG.warn("Failed to stop postmaster " + dir, e);
                            } else {
                                LOG.warn("Failed to stop postmaster " + dir + ": " + e.getMessage());
                            }
                        }
                    }
                    FileUtils.deleteDirectory(dir);
                }
            } catch (final OverlappingFileLockException e) {
                // The directory belongs to another instance in this VM.
                LOG.trace("While cleaning old data directories", e);
            } catch (final Exception e) {
                LOG.warn("While cleaning old data directories", e);
            }
        }
    }

    private String pgBin(String binaryName) {
        final String extension = SystemUtils.IS_OS_WINDOWS ? ".exe" : "";
        return new File(this.pgDir, "bin/" + binaryName + extension).getPath();
    }


    public static EmbeddedPostgres start() throws IOException {
        return builder().start();
    }

    public static EmbeddedPostgres.Builder builder() {
        return new Builder().withDefaults();
    }

    public static EmbeddedPostgres.Builder builderNoDefaults() {
        return new Builder();
    }

    private Process spawn(@Nullable String processName, List<String> commandAndArgs) throws IOException {
        final ProcessBuilder builder = new ProcessBuilder(commandAndArgs);
        builder.redirectErrorStream(true);
        builder.redirectError(errorRedirector);
        builder.redirectOutput(outputRedirector);
        final Process process = builder.start();

        processName = processName != null ? processName : process.info().command().map(FilenameUtils::getName).orElse("<unknown>");
        String name = format("%s (%d)", processName, process.pid());

        Logger outputLogger = LoggerFactory.getLogger(processName + '-' + instanceId);
        if (outputRedirector.type() == ProcessBuilder.Redirect.Type.APPEND) {
            outputLogger = LoggerFactory.getLogger(format("%s.%s-%s", this.getClass().getName(), processName, instanceId));
        }

        ProcessOutputLogger.logOutput(outputLogger, name, process);
        return process;
    }


    private void system(List<String> commandAndArgs) throws IOException {
        checkArgument(commandAndArgs.size() > 0, "No commandAndArgs given!");
        String prefix = FilenameUtils.getName(commandAndArgs.get(0));

        Process process = spawn(prefix, commandAndArgs);
        try {
            if (process.waitFor() != 0) {
                throw new IllegalStateException(format("Process %s failed%n%s", commandAndArgs,
                        IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8)));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String toString() {
        return "EmbeddedPG-" + instanceId;
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

        public EmbeddedPostgres start() throws IOException {
            // Builder Id
            final String instanceId = UUID.randomUUID().toString();

            // installation root if nothing has been set by the user.
            final File parentDirectory = EmbeddedUtil.getWorkingDirectory();

            int port = this.port != 0 ? this.port : detectPort();

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
