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

import static com.google.common.base.Preconditions.checkNotNull;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;
import javax.sql.DataSource;

import com.google.common.collect.ImmutableList;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;

/**
 * An {@link EmbeddedPostgresPreparer<DataSource>} that uses the <a href="https://flywaydb.org/">Flyway version control for your database</a> framework to
 * migrate a data source to a known state.
 */
public final class FlywayPreparer implements EmbeddedPostgresPreparer<DataSource> {

    private final ImmutableList.Builder<Consumer<FluentConfiguration>> customizers = ImmutableList.builder();

    /**
     * Creates a new instance using one or more classpath locations.
     *
     * @param locations One or more locations on the classpath.
     * @return A {@link FlywayPreparer} instance.
     */
    @Nonnull
    public static FlywayPreparer forClasspathLocation(String... locations) {
        FlywayPreparer preparer = new FlywayPreparer();
        preparer.addCustomizer(c -> c.locations(locations));
        return preparer;
    }

    /**
     * Create a new, uninitialized preparer instance. Use {@link FlywayPreparer#addCustomizer(Consumer)} to modify the configuration for the
     * {@link FluentConfiguration} object.
     */
    public FlywayPreparer() {
    }

    /**
     * Add a customizer instance. Each customizer is called once with the {@link FluentConfiguration} instance before setting the datasource and calling
     * {@link FluentConfiguration#load()} and {@link Flyway#migrate()}.
     *
     * @param customizer A {@link Consumer<FluentConfiguration>} instance. Must not be null.
     * @return This object.
     * @since 3.0
     */
    @Nonnull
    public FlywayPreparer addCustomizer(@Nonnull Consumer<FluentConfiguration> customizer) {
        checkNotNull(customizer, "customizer is null");
        customizers.add(customizer);

        return this;
    }

    /**
     * Add customizer instances. Each customizer is called once with the {@link FluentConfiguration} instance before setting the datasource and calling
     * {@link FluentConfiguration#load()} and {@link Flyway#migrate()}.
     *
     * @param customizers A set of {@link Consumer<FluentConfiguration>} instances. Must not be null.
     * @return This object.
     * @since 3.0
     */
    @Nonnull
    public FlywayPreparer addCustomizers(@Nonnull Set<Consumer<FluentConfiguration>> customizers) {
        checkNotNull(customizers, "customizers is null");
        customizers.addAll(customizers);

        return this;
    }

    @Override
    public void prepare(@Nonnull DataSource dataSource) throws IOException {
        checkNotNull(dataSource, "dataSource is null");

        try {
            final FluentConfiguration config = Flyway.configure();

            customizers.build().forEach(c -> c.accept(config));

            config.dataSource(dataSource);
            Flyway flyway = config.load();
            flyway.migrate();

        } catch (FlywayException e) {
            throw new IOException(e);
        }
    }
}
