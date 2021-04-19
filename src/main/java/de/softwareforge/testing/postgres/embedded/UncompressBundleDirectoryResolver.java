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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileLock;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

public class UncompressBundleDirectoryResolver implements PgDirectoryResolver {

    private static final Logger LOG = LoggerFactory.getLogger(UncompressBundleDirectoryResolver.class);

    private static final String INSTALL_DIRECTORY_PREFIX = "PG-";

    private static final Supplier<UncompressBundleDirectoryResolver> DEFAULT_INSTANCE_HOLDER =
            Suppliers.memoize(UncompressBundleDirectoryResolver::new);

    private static final Map<PgArchiveResolver, File> KNOWN_INSTALLATIONS = new ConcurrentHashMap<>();

    private final Lock prepareBinariesLock = new ReentrantLock();

    private final PgArchiveResolver pgArchiveResolver;

    public static synchronized UncompressBundleDirectoryResolver getDefault() {
        return DEFAULT_INSTANCE_HOLDER.get();
    }

    private UncompressBundleDirectoryResolver() {
        this(ZonkyIOPostgresArchiveResolver.INSTANCE);
    }

    public UncompressBundleDirectoryResolver(PgArchiveResolver pgArchiveResolver) {
        this.pgArchiveResolver = checkNotNull(pgArchiveResolver, "pgArchiveResolver is null");
    }

    @Override
    public File getDirectory(final File installationDirectory) {
        prepareBinariesLock.lock();
        try {
            if (KNOWN_INSTALLATIONS.containsKey(pgArchiveResolver)) {
                File pgDir = KNOWN_INSTALLATIONS.get(pgArchiveResolver);
                if (pgDir.exists()) {
                    return pgDir;
                }
            }

            final String system = EmbeddedUtil.getOS();
            final String machineHardware = EmbeddedUtil.getArchitecture();

            LOG.debug(format("Detected a %s %s system", system, machineHardware));

            String pgDigest;
            try (InputStream pgArchive = pgArchiveResolver.locatePgArchive(system, machineHardware)) {
                checkState(pgArchive != null, "No Postgres archive found for " + system + " / " + machineHardware);
                try (DigestInputStream pgArchiveData = new DigestInputStream(pgArchive, MessageDigest.getInstance("MD5"))) {
                    ByteStreams.exhaust(pgArchiveData);
                    pgDigest = BaseEncoding.base16().encode(pgArchiveData.getMessageDigest().digest());
                }
            }

            File pgDir;
            if (!installationDirectory.setWritable(true, false)) {
                LOG.warn(format("Could not make install directory %s writable!", installationDirectory));
            }

            pgDir = new File(installationDirectory, INSTALL_DIRECTORY_PREFIX + pgDigest);

            EmbeddedUtil.mkdirs(pgDir);

            final File unpackLockFile = new File(pgDir, EmbeddedPostgres.LOCK_FILE_NAME);

            if (pgDir.getName().startsWith(INSTALL_DIRECTORY_PREFIX)) {
                final File pgDirExists = new File(pgDir, ".exists");
                if (!pgDirExists.exists()) {
                    try (FileOutputStream lockStream = new FileOutputStream(unpackLockFile);
                            FileLock unpackLock = lockStream.getChannel().tryLock()) {
                        if (unpackLock != null) {
                            try {
                                checkState(!pgDirExists.exists(), "unpack lock acquired but .exists file is present " + pgDirExists);
                                LOG.info("extracting archive...");
                                InputStream x = pgArchiveResolver.locatePgArchive(system, machineHardware);
                                EmbeddedUtil.extractTxz(x, pgDir.getPath());
                                checkState(pgDirExists.createNewFile(), "couldn't make .exists file " + pgDirExists);
                            } catch (Exception e) {
                                LOG.error("while unpacking archive:", e);
                            }
                        } else {
                            // the other guy is unpacking for us.
                            int maxAttempts = 60;
                            while (!pgDirExists.exists() && --maxAttempts > 0) { // NOPMD
                                Thread.sleep(1000L);
                            }
                            checkState(pgDirExists.exists(), "Waited 60 seconds for postgres to be unpacked but it never finished!");
                        }
                    } finally {
                        if (unpackLockFile.exists() && !unpackLockFile.delete()) {
                            LOG.error(format("could not remove lock file %s", unpackLockFile.getAbsolutePath()));
                        }
                    }
                }
            }

            KNOWN_INSTALLATIONS.putIfAbsent(pgArchiveResolver, pgDir);
            LOG.debug(format("Unpacked archive at %s", pgDir));
            return pgDir;
        } catch (final IOException | NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExceptionInInitializerError(e);
        } finally {
            prepareBinariesLock.unlock();
        }
    }
}
