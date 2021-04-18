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

import java.util.Map;

import com.google.common.collect.ImmutableMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.emptyMap;

public class ConnectionInfo {

    private final String dbName;
    private final int port;
    private final String user;
    private final ImmutableMap<String, String> properties;

    public ConnectionInfo(final String dbName, final int port, final String user) {
        this(dbName, port, user, emptyMap());
    }

    public ConnectionInfo(final String dbName, final int port, final String user, final Map<String, String> properties) {
        this.dbName = checkNotNull(dbName, "dbName is null");
        this.port = port;
        this.user = checkNotNull(user, "user is null");
        this.properties = ImmutableMap.copyOf(checkNotNull(properties, "properties is null"));
    }

    public String getUser() {
        return user;
    }

    public String getDbName() {
        return dbName;
    }

    public int getPort() {
        return port;
    }

    public ImmutableMap<String, String> getProperties() {
        return properties;
    }
}
