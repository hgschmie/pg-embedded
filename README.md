# Embedded Postgres for Java

Start a PostgreSQL server for unit tests or local development.

* PostgreSQL version 9.6+
* Binaries loaded from Maven Central or locally installed
* Multiple Database servers
* Multiple databases on a single database server
* [Flyway](https://flywaydb.org/) support for Database preparation


[Full documentation site](https://pg-embedded.softwareforge.de/) [![CD from master pushes](https://github.com/hgschmie/pg-embedded/actions/workflows/master-cd.yml/badge.svg)](https://github.com/hgschmie/pg-embedded/actions/workflows/master-cd.yml)


## Manage a PostgreSQL server

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

## Manage Databases

```java
try (DatabaseManager manager = DatabaseManager.multiDatabases()
        .withInstancePreparer(EmbeddedPostgres.Builder::withDefaults)
        .build()
        .start();
        Connection c = manager.getDatabaseInfo().asDataSource().getConnection();
        Statement s = c.createStatement()) {
    try (ResultSet rs = s.executeQuery("SELECT 1")) {
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        assertFalse(rs.next());
    }
}
```



## Unit Tests with JUnit 5


```java
@RegisterExtension
public static EmbeddedPgExtension pg = MultiDatabaseBuilder.instanceWithDefaults().build();

@Test
public void simpleTest() throws SQLException {
    try (Connection c = pg.createDataSource().getConnection();
            Statement s = c.createStatement()) {
        try (ResultSet rs = s.executeQuery("SELECT 1")) {
            assertTrue(rs.next();
            assertEquals(1, return rs.getInt(1));
        }
    }
}
```

