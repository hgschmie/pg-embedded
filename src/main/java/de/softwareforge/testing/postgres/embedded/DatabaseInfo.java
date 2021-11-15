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
import static java.lang.String.format;

import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.sql.DataSource;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

/**
 * Information about a database located on a PostgreSQL server connected to an {@link EmbeddedPostgres} instance.
 */
@AutoValue
public abstract class DatabaseInfo {

    private static final String JDBC_FORMAT = "jdbc:postgresql://localhost:%d/%s?user=%s";

    /**
     * The default user used for databases.
     */
    static final String PG_DEFAULT_USER = "postgres";

    /**
     * The default database name.
     */
    static final String PG_DEFAULT_DB = "postgres";

    /**
     * Returns the name of the database created.
     *
     * @return Name of the database. Is never null.
     */
    @Nonnull
    public abstract String dbName();

    /**
     * Returns the TCP port for the database server.
     *
     * @return A port number. May be -1 if this objects represents an error connection.
     */
    public abstract int port();

    /**
     * Returns the user that can connect to this database.
     *
     * @return The user name. Is never null.
     */
    @Nonnull
    public abstract String user();

    /**
     * Returns all properties that are be applied to a new data source connection to this database. See
     * <a href="https://jdbc.postgresql.org/documentation/head/connect.html#connection-parameters">the
     * PostgreSQL JDBC driver documentation</a> for a comprehensive list.
     *
     * @return Map of key-value pairs representing data source connection properties.
     */
    public abstract ImmutableMap<String, String> connectionProperties();

    abstract Optional<SQLException> exception();

    static Builder builder() {
        return new AutoValue_DatabaseInfo.Builder()
                .dbName(PG_DEFAULT_DB)
                .user(PG_DEFAULT_USER);
    }

    static DatabaseInfo forException(SQLException e) {
        return builder().exception(e).port(-1).build();
    }

    /**
     * Returns a JDBC url to connect to the described database.
     * @return A JDBC url that can be used to connect to the database. Never null.
     */
    @Nonnull
    public String asJdbcUrl() {
        checkState(exception().isEmpty(), "DatabaseInfo contains SQLException: %s", exception());

        String additionalParameters = connectionProperties().entrySet().stream()
                .map(e -> format("&%s=%s", e.getKey(), e.getValue()))
                .collect(Collectors.joining());
        return format(JDBC_FORMAT, port(), dbName(), user()) + additionalParameters;
    }

    /**
     * Returns a {@link DataSource} instance connected to the described database.
     *
     * @return An initialized {@link DataSource} object. Never null.
     * @throws SQLException A problem occured trying to connect to the database.
     */
    public DataSource asDataSource() throws SQLException {
        if (exception().isPresent()) {
            throw exception().get();
        }

        return EmbeddedPostgres.createDataSource(user(), dbName(), port(), connectionProperties());
    }

    @AutoValue.Builder
    abstract static class Builder {

        abstract Builder dbName(String dbName);

        abstract Builder port(int port);

        abstract Builder user(String user);

        abstract Builder exception(SQLException exception);

        abstract ImmutableMap.Builder<String, String> connectionPropertiesBuilder();

        final Builder addConnectionProperty(String key, String value) {
            connectionPropertiesBuilder().put(key, value);
            return this;
        }

        abstract Builder connectionProperties(ImmutableMap<String, String> connectionProperties);

        abstract DatabaseInfo build();

    }
}
