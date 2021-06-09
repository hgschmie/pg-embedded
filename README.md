# Embedded Postgres for Java

Start a real Postgres engine for unit tests or local development.

## Basic Usage

A postgres instance is started and stopped with the `EmbeddedPostgres` class:

```java
try (EmbeddedPostgres pg = EmbeddedPostgres.defaultInstance()) {
    Connection c = pg.getDatabase().getConnection();
    Statement s = c.createStatement()) {
    try (ResultSet rs = s.executeQuery("SELECT 1")) {
        if (rs.next()) {
            return rs.getInt(1));
        }
    }
}
```

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
