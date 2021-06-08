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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import de.softwareforge.testing.postgres.embedded.EmbeddedPostgres;
import de.softwareforge.testing.postgres.embedded.SchemaInfo;
import de.softwareforge.testing.postgres.embedded.SchemaManager;

import java.io.IOException;
import java.sql.SQLException;
import javax.sql.DataSource;

import com.google.common.annotations.VisibleForTesting;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EmbeddedPgExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPgExtension.class);

    private final SchemaManager.Builder<SchemaManager> preparedDbProviderBuilder;

    // whether the instance is created per test method or once for all test methods.
    // depends on whether the instance is bound as a static field or a per instance field.
    // if the beforeAll method is not called, it operates in per-test mode, otherwise only
    // a single instance of the preparer is created;
    private volatile boolean perTestMode = true;
    private volatile SchemaManager schemaManager = null;

    private EmbeddedPgExtension(SchemaManager.Builder<SchemaManager> preparedDbProviderBuilder) {
        this.preparedDbProviderBuilder = preparedDbProviderBuilder;
    }

    public static NewExtensionBuilder multiDatabase() {
        return new NewExtensionBuilder(true);
    }

    public static NewExtensionBuilder singleDatabase() {
        return new NewExtensionBuilder(false);
    }

    /**
     * Returns the data source for the current instance.
     */
    public DataSource getDatabase() throws SQLException {
        return getConnectionInfo().asDataSource();
    }

    @VisibleForTesting
    EmbeddedPostgres getEmbeddedPostgres() {
        return schemaManager.getEmbeddedPostgres();
    }

    /**
     * Returns a {@link SchemaInfo} describing the database connection.
     */
    public SchemaInfo getConnectionInfo() throws SQLException {
        checkState(schemaManager != null, "no before method has been called!");
        SchemaInfo schemaInfo = schemaManager.getConnectionInfo();
        LOG.info("Connection to {}", schemaInfo);
        return schemaInfo;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        this.perTestMode = false;
        startDbProvider();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        checkNotNull(extensionContext, "extensionContext is null");

        if (!perTestMode) {
            stopDbProvider();
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        checkNotNull(extensionContext, "extensionContext is null");

        if (perTestMode) {
            startDbProvider();
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        checkNotNull(extensionContext, "extensionContext is null");

        if (perTestMode) {
            stopDbProvider();
        }
    }

    private void startDbProvider() throws SQLException, IOException {
        this.schemaManager = preparedDbProviderBuilder.build();
        this.schemaManager.start();
    }

    private void stopDbProvider() throws Exception {
        SchemaManager provider = this.schemaManager;

        this.perTestMode = true;
        this.schemaManager = null;
        if (provider != null) {
            provider.close();
        }
    }

    static class NewExtensionBuilder extends SchemaManager.Builder<EmbeddedPgExtension> {

        private NewExtensionBuilder(boolean useTemplate) {
            super(useTemplate);
        }

        @Override
        public EmbeddedPgExtension build() {
            SchemaManager.Builder<SchemaManager> preparedDbProviderBuilder = new SchemaManager.PreparedDbProviderBuilder(multiMode)
                    .withPreparer(schemaPreparer);
            customizers.build().forEach(preparedDbProviderBuilder::withCustomizer);
            return new EmbeddedPgExtension(preparedDbProviderBuilder);
        }
    }
}
