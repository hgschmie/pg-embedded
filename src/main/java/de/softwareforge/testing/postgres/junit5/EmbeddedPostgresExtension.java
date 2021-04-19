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

import de.softwareforge.testing.postgres.embedded.DatabasePreparer;
import de.softwareforge.testing.postgres.embedded.EmbeddedPostgres;
import de.softwareforge.testing.postgres.embedded.EmbeddedPostgres.Builder;
import org.junit.jupiter.api.extension.Extension;

public final class EmbeddedPostgresExtension {

    private EmbeddedPostgresExtension() {}

    /**
     * Create a vanilla Postgres cluster -- just initialized, no customizations applied.
     *
     * @return A {@link SingleInstancePostgresExtension} object.
     */
    public static SingleInstancePostgresExtension singleInstance() {
        return new SingleInstancePostgresExtension();
    }

    /**
     * Create a vanilla Postgres cluster with standard initializations ({@link EmbeddedPostgres.Builder#withDefaults()}).
     *
     * @return A {@link SingleInstancePostgresExtension} object.
     */
    public static SingleInstancePostgresExtension singleInstanceWithDefaults() {
        return singleInstance().customize(EmbeddedPostgres.Builder::withDefaults);
    }

    /**
     * Returns a {@link Extension} to create a Postgres cluster, shared amongst all test cases in this JVM. The rule contributes Config switches to configure
     * each test case to get its own database.
     *
     * @param preparer A {@link DatabasePreparer} that will initialize any {@link javax.sql.DataSource} objects before use.
     * @return A {@link PreparedDbExtension} object.
     */
    public static PreparedDbExtension preparedDatabase(DatabasePreparer preparer) {
        return new PreparedDbExtension(preparer);
    }

    /**
     * Returns a {@link Extension} to create a Postgres cluster, shared amongst all test cases in this JVM. The rule contributes Config switches to configure
     * each test case to get its own database. The cluster has been configured with standard initializations ({@link Builder#withDefaults()}).
     *
     * @param preparer A {@link DatabasePreparer} that will initialize any {@link javax.sql.DataSource} objects before use.
     * @return A {@link PreparedDbExtension} object.
     */
    public static PreparedDbExtension preparedDatabaseWithDefaults(DatabasePreparer preparer) {
        return preparedDatabase(preparer).customize(EmbeddedPostgres.Builder::withDefaults);
    }

}
