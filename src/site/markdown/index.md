# Embedded PostgreSQL for JVM

Start a PostgreSQL server for unit testing or local development.

This library controls PostgreSQL server instances and gives easy access to one or more databases on the database server. Using `pg-embedded` makes unit and integration tests simple and allows easy development without having to install local binaries.

## How it works

The library spins up one or more native PostgreSQL servers up and controls their lifecycle through `EmbeddedPostgres` instances. When an instance is closed, the database server is shut down.

`EmbeddedPostgres` class instances manage a PostgreSQL server. Each server is independent, multiple instances will manage multiple, independent servers. This allows testing of more complex systems that may use multiple databases.

`DatabaseManager` provides an API to manage one or more database instances on a PostgreSQL server. Each `DatabaseManager` manages a PostgreSQL server.

pg-embedded supports the JUnit5 test framework directly through the `EmbeddedPgExtension`.

## How to use it

* [Using EmbeddedPostgres](using_embedded_postgres.html) to control PostgreSQL server instances.
* [Using DatabaseManager](using_database_manager.html) to manage databases.

## Testing with pg-embedded

* Using pg-embedded with JUnit 5


## Reference

* [Javadoc](apidocs/)
* [Building the code]
* [Contributing]


## JUnit 5 usage

Only JUnit 5 is supported!

```java
@RegisterExtension
EmbeddedPgExtension pg = SingleDatabaseBuilder.instance().build();

@Test
public void simpleTest() throws SQLException {
    try (Connection c = pg.getDatabase().getConnection();
        Statement s = c.createStatement()) {
        try (ResultSet rs = s.executeQuery("SELECT 1")) {
            assertTrue(rs.next();
            assertEquals(1, return rs.getInt(1));
        }
    }
}
```

### Features

* class member (static) extension field reuses the same postgres instance for all test cases, instance member will use a new postgres instance for every test case.
* `SingleDatabaseBuilder` will use the same database for all calls to `EmbeddedPgExtension#createDatabaseInfo()` and `EmbeddedPgExtension#createDataSource()`. `MultiDatabaseBuilder` will create a new database on every call.
* `instance()` returns a builder that can be customized with preparer and customizers.
* `instanceWithDefaults()` returns a builder with defaults applied that can be customized.
* `preparedInstance()` takes a database preparer and returns a builder.
* `preparedInstanceWithDefaults()` takes a database preparer, applies defaults and returns a builder.


## Migrators (Flyway)

```java
@RegisterExtension
public static EmbeddedPgExtension singleDatabase =
    SingleDatabaseBuilder.preparedInstanceWithDefaults(
        FlywayPreparer.forClasspathLocation("db/testing"))
    .build();
```
