# Using DatabaseManager

The [DatabaseManager](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.html) class allows creation and control
of one or more database instances on a PostgreSQL server.

By default, each PostgreSQL server has two databases:

* the `template1` template database from which all other databases are cloned
* the `postgres` default database.

Additional databases can be created using the standard SQL `CREATE DATABASE` commands.

[DatabaseManager](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.html) has two operation modes:

* In **single database** mode, only a single database exists (the `postgres` database). Every call to `DatabaseManager.getDatabaseInfo()` returns a [DatabaseInfo](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseInfo.html) instance that can be used to connect to the single database.
* In **multi database** mode, every call to `DatabaseManager.getDatabaseInfo()` creates a new database on the PostgreSQL server and returns a [DatabaseInfo](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseInfo.html) instance that decribes this new database. Each database has a unique name and is cloned from the `template1` database, so any operations on the template (creating tables, loading plugins etc.) are also applied to each new database instance.

Each [DatabaseManager](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.html) instance manages an [EmbeddedPostgres](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.html) instance to control the database server. Calling `DatabaseManager.close()` shuts down the PostgreSQL server instance.

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

A [DatabaseManager](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.html) is created by calling either `DatabaseManager.singleDatabase()` or `DatabaseManager.multiDatabases()`. Each call returns a [Builder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.Builder.html) which allows further customization of the PostgreSQL server and the databases. The builder creates the [DatabaseManager](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.html) instance by calling the `Builder.build()` method.

Every [DatabaseManager](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.html) instance must be explicitly started by calling the `start()` method.

## Customizing the DatabaseManager

### Customizing the EmbeddedPostgres instance

The `Builder.withInstancePreparer()` and `Builder.withInstancePreparers()` method allow the customization of the PostgreSQL server configuration. An instance preparer is executed once when the [DatabaseManager](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.html) is started and the [EmbeddedPostgres.Builder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.Builder.html) that creates the [EmbeddedPostgres](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.html) instance is passed to it.
Either method accepts [EmbeddedPostgresPreparer](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgresPreparer.html) instances. See the [EmbeddedPostgres documentation](using_embedded_postgres.html) for details.

The `Builder.withDefaults()` method on the [EmbeddedPostgres.Builder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.Builder.html) can be used as a method reference by calling `Builder.withInstancePreparer(EmbeddedPostgres.Builder::withDefaults)` to create a default configuratrion for the PostgreSQL server.

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

The `Builder.withDatabasePreparer()` and `Builder.withDatabasePreparers()` methods allow registration of Database customizations. A database preparer is executed once when the
[DatabaseManager](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.html) is started and a `DataSource` instance connected to the default database will be passed to it. This `DataSource` is either connected to the default database (in single database mode) or the template database from which all databases are cloned (in multi database mode).

Any DDL or data load operation will be visible in every database managed by the [DatabaseManager](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.html). The most common use case is preparing the database for testing.

Either method accepts [EmbeddedPostgresPreparer](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgresPreparer.html) instances.

See [Using Flyway](using_flyway.html) for database customizations with the Flyway migration framework.
