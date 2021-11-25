# Using EmbeddedPostgres to control PostgreSQL server instances

Each EmbeddedPostgres instance manages a PostgreSQL server. It supports starting and stopping the server and basic configuration.

Minimal code to start a PostgreSQL server.

```java
try (EmbeddedPostgres pg = EmbeddedPostgres.defaultInstance();
        Connection c = pg.createDefaultDataSource().getConnection();
        Statement s = c.createStatement()) {

    try (ResultSet rs = s.executeQuery("SELECT 1")) {
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        assertFalse(rs.next());
    }
}
```

This code snippet starts a PostgreSQL server with default settings (see the `EmbeddedPostgres.Builder#withDefaults()` method for details). The instance will terminate when the `EmbeddedPostgres` class is closed, so it can be used with the try-with-resources construct.

Further customization can be done using a builder:

```java
try (EmbeddedPostgres pg = EmbeddedPostgres.builder()
        .addConnectionProperty(..., ...)
        .addInitDbConfiguration(..., ...)
        .addServerConfiguration(..., ...)
        .build();
        Connection c = pg.createDefaultDataSource().getConnection();
        Statement s = c.createStatement()) {

    try (ResultSet rs = s.executeQuery("SELECT 1")) {
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        assertFalse(rs.next());
    }
}
```

## Customization

See the [EmbeddedPostgres.Builder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.Builder.html) Javadoc for further information.

### Common settings

* `addInitDbConfiguration` adds parameters for the `initdb` command that creates the initial configuration of the PostgreSQL server. Parameters that do not require a value can be added by using the empty string as value.
* `addServerConfiguration` adds parameters for the `postgres` server command to start and stop the server.
* `addConnectionProperty` adds parameters that are applied to every DataSource object created with the various DataSource creation methods on the `EmbeddedPostgres` class.

### Controlling the Postgres installation

By default, pg-embedded will use an internal mechanism to resolve Postgres binaries and manage the data directories. There are a number of builder methods to influence this mechanism.

* `setServerVersion` controls the version of PostgreSQL. By default, version "13" is used. This version is a partial or full version string and it is resolved against the available artifacts on Maven Central. Not all PostgreSQL versions are available and pg-embedded only supports version 10 or newer. Please see the "choosing the postgres version" page for more information on how pg-embedded selects a postgres version.
* `setPort` changes the port allocation mechanism. By default, `pg-embedded` will choose a random, unallocated TCP port and start the server on that port. Calling the `setPort` method will use the specific TCP port. Please note that, if the port is not available, starting the server will fail.
* `setServerStartupWait` sets the timeout to wait for the PostgreSQL server to start. The default is 10 seconds.
* `setRemoveDataOnShutdown` controls whether pg-embedded cleans up its data directory. Sometimes it is desirable to reuse or inspect the state of the postgres data directories after the `EmbeddedPostgres` code has finished. Calling this method with `false` will preserve the data directory.
* `setDataDirectory` controls the base path where data directories are created. Within this folder, each database instance will create its own data directory prefixed with `epd-`. These folders are deleted when `EmbeddedPostgres` is shut down unless the `setRemoveDataOnShutdown` option has been set to false.
* `setInstallationBaseDirectory` controls where the binary Postgres distributions are unpacked. Installations are prefixed with `PG-`. Postgres installation are only unpacked once.

Advanced uses including using a locally installed Postgres are documented on the [Advanced Usage](advanced_usage.html) page.
