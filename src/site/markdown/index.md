# Embedded PostgreSQL for JVM

Start a PostgreSQL server for unit testing or local development.

This library controls PostgreSQL server instances and gives easy access to one or more databases on the database server. Using `pg-embedded` makes unit and integration tests simple and allows easy development without having to install local binaries.

## How it works

The library spins up one or more native PostgreSQL servers up and controls their lifecycle through `EmbeddedPostgres` instances. When an instance is closed, the database server is shut down.


An instance of the `EmbeddedPostgres` class manages a single PostgreSQL server. Each server is independent, multiple instances will manage multiple, independent servers. This allows testing of more complex systems that may use multiple databases.

## Supported Platforms

* Linux/x86_64 (tested on RHEL/CentOS, AWS Linux, Debian Linux)
* Linux/aarch64 (AWS Graviton, other systems that support ARM64v8 should work as well)
* Linux/aarch32 (Raspberry Pi 3B+ tested, other systems that support ARM32v7 should work as well)
* MacOSX/x86_64 (tested on MacOS 11.5 and MacOS 11.6)

### Untested

* MacOSX/aarch64
  * the PostgreSQL binaries used do not yet support aarch64 natively, so the x86_64 binaries are used and executed through Rosetta. When "fat" are available, they should be picked up and just work. Once native MacOS X/aarch64 binaries are available, setting the `pg-embedded.prefer-native` system property to `true` will enable pg-embedded to use those directly.

* Windows/x86_64 and Windows/i386
  * untested, patches welcome

* Linux Alpine variants
  * Alpine Linux uses special builds of the PostgreSQL


## How to use it

* Using EmbeddedPostgres directly
* Using DatabaseManager to manage one or more databases
* Advanced Topics
* FAQ

## Testing with pg-embedded

* Using pg-embedded with JUnit 5


## Reference

* Javadoc
* Building the library
* Contributing




### Using `EmbeddedPostgres`

Creating a new instance of the `EmbeddedPostgres` class starts a new database server.


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

The PostgreSQL server contains the standard `template1` and `postgres` databases. DataSource instances that will connect to those databases can be created by using `createDefaultDataSource` and `createTemplateDataSource` respectively.


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
