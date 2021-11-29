# Using DatabaseManager

The [DatabaseManager](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.html) class allows creation and control
of one or more database instances on a PostgreSQL server.

By default, each PostgreSQL server has two databases:

* the `template1` template database from which all other databases are cloned
* the `postgres` default database.

Additional databases can be created using the standard SQL `CREATE DATABASE` commands.

`DatabaseManager` has two operation modes:

* In **single database** mode, only a single database exists (the `postgres` database). Every call to `getDatabaseInfo()` returns a [DatabaseInfo](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseInfo.html) instance that can be used to connect to the single database.
* In **multi database** mode, every call to `getDatabaseInfo()` creates a new database on the PostgreSQL server and returns a [DatabaseInfo](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseInfo.html) instance that decribes this new database. Each database has a unique name and is cloned from the `template1` database, so any operations on the template (creating tables, loading plugins etc.) are also applied to each new database instance.

Each `DatabaseManager` instance manages an [EmbeddedPostgres](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.html) instance to control the database server. Calling `close()` on the `DatabaseManager` instance shuts down the PostgreSQL server instance.

```java
try (DatabaseManager manager = DatabaseManager.singleDatabase()
    // same as EmbeddedPostgres.defaultInstance()
    .withInstancePreparer(EmbeddedPostgres.Builder::withDefaults)
    .build()
    .start()) {
    DatabaseInfo databaseInfo = manager.getDatabaseInfo();
    try (Connection c = databaseInfo.asDataSource().getConnection();
        Statement s = c.createStatement()) {
        try (ResultSet rs = s.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
            assertFalse(rs.next());
        }
    }
}
```

A `DatabaseManager` is created by calling either `DatabaseManager.singleDatabase()` or `DatabaseManager.multiDatabase()`. Each call returns a Builder which allows further customization of the PostgreSQL server and the databases.

The builder creates the `DatabaseManager` instance by calling `build()`. A `DatabaseManager` must be explicitly started by calling the `start()` method.

## Customizing the DatabaseManager

### Customizing the EmbeddedPostgres instance

The `withInstancePreparer` and `withInstancePreparers` builder method allows PostgreSQL server configuration customizations. Any instance preparer gets the `EmbeddedPostgres.Builder` instance passed in, that is subsequently used to configure the `EmbeddedPostgres` instance. The `withInstancePreparer` method takes an [EmbeddedPostgresPreparer](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgresPreparer.html) instance. See the [EmbeddedPostgres documentation}(using_embedded_postgres.html) for details.

The `EmbeddedPostgres.Builder#withDefaults()` method can be used as method reference by calling `withInstancePreparer(EmbeddedPostgres.Builder::withDefaults)`.

```java
try (DatabaseManager manager = DatabaseManager.singleDatabase()
    // set the default connection timeout for all DataSources
    .withInstancePreparer(builder -> builder.addConnectionProperty("connectTimeout", "20"))
    .build()
    .start()) {
    ...
}
```

### Customizing the Database instances

The `withDatabasePreparer` and `withDatabasePreparers` builder methods allow the registration of Database customization code. Any database preparer is called once when the `DatabaseManager` is started and gets a `DataSource` instance passed which is connected to a template database. Any DDL or data load operation
will be visible in every database managed by the `DatabaseManager`. The most common use case is preparing the database e.g. for a unit test.

pg-embedded offers the `FlywayPreparer` that uses the [Flyway Database migration framework](https://flywaydb.org/) for DDL preparation and database migration.

```java
try (DatabaseManager manager = DatabaseManager.multiDatabase()
    .withInstancePreparer(EmbeddedPostgres.Builder::withDefaults)

    // apply all flyway migrations from the db/testing classpath location
    // to every database handed out by this manager
    .withDatabasePreparer(FlywayPreparer.forClasspathLocation("db/testing"))

    .build()
.start()) {
    ...
}
```
