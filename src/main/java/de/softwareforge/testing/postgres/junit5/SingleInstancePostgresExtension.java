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

import java.io.IOException;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import de.softwareforge.testing.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class SingleInstancePostgresExtension implements AfterTestExecutionCallback, BeforeTestExecutionCallback {

    private volatile EmbeddedPostgres epg;
    private volatile Connection postgresConnection;
    private final List<Consumer<EmbeddedPostgres.Builder>> builderCustomizers = new CopyOnWriteArrayList<>();

    SingleInstancePostgresExtension() { }

    @Override
    public void beforeTestExecution(ExtensionContext extensionContext) throws Exception {
        checkNotNull(extensionContext, "extensionContext is null");

        this.epg = pg();
        postgresConnection = epg.getPostgresDatabase().getConnection();
    }

    private EmbeddedPostgres pg() throws IOException {
        final EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder();

        builderCustomizers.forEach(c -> c.accept(builder));

        return builder.start();
    }

    public SingleInstancePostgresExtension customize(Consumer<EmbeddedPostgres.Builder> customizer) {
        checkNotNull(customizer, "customizer is null");
        checkState(epg == null, "already started");
        builderCustomizers.add(customizer);

        return this;
    }

    public EmbeddedPostgres getEmbeddedPostgres() {
        final EmbeddedPostgres epg = this.epg;
        checkState(epg != null, "JUnit test not started yet!");

        return epg;
    }

    @Override
    public void afterTestExecution(ExtensionContext extensionContext) throws Exception {
        checkNotNull(extensionContext, "extensionContext is null");

        try {
            postgresConnection.close();
        } finally {
            epg.close();
        }
    }
}
