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

import de.softwareforge.testing.postgres.embedded.EmbeddedPostgres;
import de.softwareforge.testing.postgres.embedded.DatabaseManager;
import de.softwareforge.testing.postgres.embedded.DatabasePreparer;

/**
 * Create a new cluster that supports multiple databases. Each database is cloned from a template.
 */
public final class SingleDatabaseBuilder {

    private SingleDatabaseBuilder() {
        throw new AssertionError("MultiDatabaseBuilder can not be instantiated");
    }

    /**
     * Create a vanilla database -- just initialized, no customizations applied.
     */
    public static DatabaseManager.Builder<EmbeddedPgExtension> instance() {
        return EmbeddedPgExtension.singleDatabase();
    }

    /**
     * Create a vanilla database with standard initializations ({@link EmbeddedPostgres.Builder#withDefaults()}).
     */
    public static DatabaseManager.Builder<EmbeddedPgExtension> instanceWithDefaults() {
        return EmbeddedPgExtension.singleDatabase().withCustomizer(EmbeddedPostgres.Builder::withDefaults);
    }

    /**
     * Create a vanilla database and execute a {@link DatabasePreparer} for initialization on it.
     */
    public static DatabaseManager.Builder<EmbeddedPgExtension> preparedInstance(DatabasePreparer preparer) {
        return EmbeddedPgExtension.singleDatabase().withPreparer(preparer);
    }

    /**
     * Create a vanilla database with defaults and execute a {@link DatabasePreparer} for initialization on it.
     */
    public static DatabaseManager.Builder<EmbeddedPgExtension> preparedInstanceWithDefaults(DatabasePreparer preparer) {
        return EmbeddedPgExtension.singleDatabase().withPreparer(preparer).withCustomizer(EmbeddedPostgres.Builder::withDefaults);
    }
}
