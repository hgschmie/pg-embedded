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






In your JUnit test just add:

```java
@Rule
public SingleInstancePostgresRule pg = EmbeddedPostgresRules.singleInstance();
```

This simply has JUnit manage an instance of EmbeddedPostgres (start, stop). You can then use this to get a DataSource with: `pg.getEmbeddedPostgres().getPostgresDatabase();`  

Additionally you may use the [`EmbeddedPostgres`](src/main/java/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.java) class directly by manually starting and stopping the instance; see [`EmbeddedPostgresTest`](src/test/java/com/opentable/db/postgres/embedded/EmbeddedPostgresTest.java) for an example.

Default username/password is: postgres/postgres and the default database is 'postgres'

## Migrators (Flyway or Liquibase)

You can easily integrate Flyway or Liquibase database schema migration:
##### Flyway
```java
@Rule 
public PreparedDbRule db =
    EmbeddedPostgresRules.preparedDatabase(
        FlywayPreparer.forClasspathLocation("db/my-db-schema"));
```

##### Liquibase
```java
@Rule
public PreparedDbRule db = 
    EmbeddedPostgresRules.preparedDatabase(
            LiquibasePreparer.forClasspathLocation("liqui/master.xml"));
```

This will create an independent database for every test with the given schema loaded from the classpath.
Database templates are used so the time cost is relatively small, given the superior isolation truly
independent databases gives you.

## Postgres version

The JAR file contains bundled version of Postgres. You can pass different Postgres version by implementing [`PgBinaryResolver`](src/main/java/de/softwareforge/testing/postgres/embedded/PgBinaryResolver.java).

Example:
```java
class ClasspathBinaryResolver implements PgBinaryResolver {
    public InputStream getPgBinary(String system, String machineHardware) throws IOException {
        ClassPathResource resource = new ClassPathResource(format("postgresql-%s-%s.txz", system, machineHardware));
        return resource.getInputStream();
    }
}

EmbeddedPostgreSQL
            .builder()
            .setPgBinaryResolver(new ClasspathBinaryResolver())
            .start();

```

## Windows

If you experience difficulty running `otj-pg-embedded` tests on Windows, make sure
you've installed the appropriate MFC redistributables.

* [Microsoft Site](https://support.microsoft.com/en-us/help/2977003/the-latest-supported-visual-c-downloads])
* [Github issue discussing this](https://github.com/opentable/otj-pg-embedded/issues/65)

----
Copyright (C) 2017 OpenTable, Inc
