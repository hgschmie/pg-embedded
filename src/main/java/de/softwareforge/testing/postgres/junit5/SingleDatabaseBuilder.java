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

import de.softwareforge.testing.postgres.embedded.DatabaseManager;
import de.softwareforge.testing.postgres.embedded.DatabasePreparer;
import de.softwareforge.testing.postgres.embedded.EmbeddedPostgres;
import de.softwareforge.testing.postgres.embedded.EmbeddedPostgresPreparer;

import javax.annotation.Nonnull;
import javax.sql.DataSource;

/**
 * Create a new PostgreSQL server that supports a single database.
 */
public final class SingleDatabaseBuilder {

    private SingleDatabaseBuilder() {
        throw new AssertionError("SingleDatabaseBuilder can not be instantiated");
    }

    /**
     * Create a builder without any customizations applied.
     *
     * @return A {@link DatabaseManager.Builder<EmbeddedPgExtension>} instance that can be customized further.
     */
    public static DatabaseManager.Builder<EmbeddedPgExtension> instance() {
        return EmbeddedPgExtension.singleDatabase();
    }

    /**
     * Create a builder with standard initializations ({@link EmbeddedPostgres.Builder#withDefaults()}) applied.
     *
     * @return A {@link DatabaseManager.Builder<EmbeddedPgExtension>} instance that can be customized further.
     */
    public static DatabaseManager.Builder<EmbeddedPgExtension> instanceWithDefaults() {
        return EmbeddedPgExtension.singleDatabase().withInstancePreparer(EmbeddedPostgres.Builder::withDefaults);
    }

    /**
     * @deprecated Use {@link #preparedInstance(EmbeddedPostgresPreparer)}.
     */
    @Deprecated
    public static DatabaseManager.Builder<EmbeddedPgExtension> preparedInstance(@Nonnull DatabasePreparer preparer) {
        return EmbeddedPgExtension.singleDatabase().withPreparer(preparer);
    }

    /**
     * @deprecated Use {@link #preparedInstanceWithDefaults(EmbeddedPostgresPreparer)}.
     */
    @Deprecated
    public static DatabaseManager.Builder<EmbeddedPgExtension> preparedInstanceWithDefaults(@Nonnull DatabasePreparer preparer) {
        return EmbeddedPgExtension.singleDatabase().withPreparer(preparer).withCustomizer(EmbeddedPostgres.Builder::withDefaults);
    }

    /**
     * Create a builder and register a {@link EmbeddedPostgresPreparer<DataSource>} to set up the database.
     *
     * @param dataSourcePreparer A {@link EmbeddedPostgresPreparer<DataSource>} instance. Must not be null.
     * @return A {@link DatabaseManager.Builder<EmbeddedPgExtension>} instance that can be customized further.
     */
    public static DatabaseManager.Builder<EmbeddedPgExtension> preparedInstance(@Nonnull EmbeddedPostgresPreparer<DataSource> dataSourcePreparer) {
        return EmbeddedPgExtension.singleDatabase().withDataSourcePreparer(dataSourcePreparer);
    }

    /**
     * Create a builder with standard initializations ({@link EmbeddedPostgres.Builder#withDefaults()}) applied and register a {@link
     * EmbeddedPostgresPreparer<DataSource>} to set up the database.
     *
     * @param dataSourcePreparer A {@link EmbeddedPostgresPreparer<DataSource>} instance. Must not be null.
     * @return A {@link DatabaseManager.Builder<EmbeddedPgExtension>} instance that can be customized further.
     */
    public static DatabaseManager.Builder<EmbeddedPgExtension> preparedInstanceWithDefaults(@Nonnull EmbeddedPostgresPreparer<DataSource> dataSourcePreparer) {
        return EmbeddedPgExtension.singleDatabase().withDataSourcePreparer(dataSourcePreparer).withInstancePreparer(EmbeddedPostgres.Builder::withDefaults);
    }
}
