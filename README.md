[![Main Build](https://github.com/hgschmie/pg-embedded/actions/workflows/master-cd.yml/badge.svg)](https://github.com/hgschmie/pg-embedded/actions/workflows/master-cd.yml)

----
*pg-embedded is not affected by the [xz supply chain attack attempt](https://nvd.nist.gov/vuln/detail/CVE-2024-3094)! All releases of pg-embedded have shipped with xz 1.10 or earlier, which either predates the attack (1.9) or was released after the attack was mitigated (1.10). pg-embedded never shipped with a version that contains potentially compromising commits.*

----
# Embedded Postgres for Java

Start a PostgreSQL server for unit tests or local development.

Please check out the [full documentation site](https://pg-embedded.softwareforge.de/)!

* PostgreSQL version 11+ (see https://www.postgresql.org/docs/ for all supported versions of PostgreSQL).
* Binaries loaded from Maven Central (using the [Zonky embedded Postgres Binaries](https://github.com/zonkyio/embedded-postgres-binaries)) or locally installed.
* Multiple Database servers
* Multiple databases on a single database server
* [Flyway](https://flywaydb.org/) support for Database preparation



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
