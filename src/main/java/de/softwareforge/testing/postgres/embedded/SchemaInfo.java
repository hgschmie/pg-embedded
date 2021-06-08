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

import static com.google.common.base.Preconditions.checkState;

import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

@AutoValue
public abstract class SchemaInfo {

    public static final String JDBC_FORMAT = "jdbc:postgresql://localhost:%d/%s?user=%s";

    // default user used for databases
    public static final String PG_DEFAULT_USER = "postgres";
    public static final String PG_DEFAULT_DB = "postgres";


    public abstract String dbName();

    public abstract int port();

    public abstract String user();

    public abstract ImmutableMap<String, String> properties();

    public abstract Optional<SQLException> exception();

    public static Builder builder() {
        return new AutoValue_SchemaInfo.Builder()
                .dbName(PG_DEFAULT_DB)
                .user(PG_DEFAULT_USER);
    }

    public static SchemaInfo forException(SQLException e) {
        return builder().exception(e).port(-1).build();
    }

    public String asJdbcUrl() {
        checkState(exception().isEmpty(), "SchemaInfo contains SQLException: %s", exception());

        String additionalParameters = properties().entrySet().stream()
                .map(e -> String.format("&%s=%s", e.getKey(), e.getValue()))
                .collect(Collectors.joining());
        return String.format(JDBC_FORMAT, port(), dbName(), user()) + additionalParameters;
    }

    public DataSource asDataSource() throws SQLException {
        checkState(exception().isEmpty(), "SchemaInfo contains SQLException: %s", exception());

        return EmbeddedPostgres.getDatabase(user(), dbName(), port(), properties());
    }

    public String asString() {
        if (exception().isPresent()) {
            return "<no connection>: " + exception().toString();
        } else {
            return asJdbcUrl();
        }
    }


    @AutoValue.Builder
    public abstract static class Builder {

        public abstract Builder dbName(String dbName);

        public abstract Builder port(int port);

        public abstract Builder user(String user);

        abstract Builder exception(SQLException exception);

        abstract ImmutableMap.Builder<String, String> propertiesBuilder();

        public final Builder addProperty(String key, String value) {
            propertiesBuilder().put(key, value);
            return this;
        }

        public abstract Builder properties(ImmutableMap<String, String> properties);

        public abstract SchemaInfo build();

    }
}
