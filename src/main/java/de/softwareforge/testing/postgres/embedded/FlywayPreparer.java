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

import java.sql.SQLException;
import java.util.function.Consumer;

import javax.sql.DataSource;

import com.google.common.collect.ImmutableList;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO: Detect missing migration files.
// cf. https://github.com/flyway/flyway/issues/1496
// There is also a related @Ignored test in otj-sql.

public class FlywayPreparer implements DatabasePreparer {

    private final ImmutableList.Builder<Consumer<FluentConfiguration>> customizers = ImmutableList.builder();

    public static FlywayPreparer forClasspathLocation(String... locations) {
        FlywayPreparer preparer = new FlywayPreparer();
        preparer.customize(c -> c.locations(locations));
        return preparer;
    }

    protected FlywayPreparer() {
    }

    public FlywayPreparer customize(Consumer<FluentConfiguration> customizer) {
        checkNotNull(customizer, "customizer is null");
        customizers.add(customizer);

        return this;
    }

    @Override
    public void prepare(DataSource ds) throws SQLException {
        final FluentConfiguration config = Flyway.configure();

        customizers.build().forEach(c -> c.accept(config));

        config.dataSource(ds);
        config.load().migrate();
    }
}
