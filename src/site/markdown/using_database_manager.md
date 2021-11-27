# Using DatabaseManager to manage databases

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

## Customizing the DatabaseManager

`DatabaseManager` supports two sets of customizations:

* PostgreSQL server customizations using `withInstancePreparer`. Any instance preparer gets the `EmbeddedPostgres.Builder` instance passed in that is subsequently used to configure the `EmbeddedPostgres` instance. This method takes an [EmbeddedPostgresPreparer](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgresPreparer.html) instance, which is similar to a consumer except that the `prepare()` method can also throw `IOException` or `SQLException`. 
