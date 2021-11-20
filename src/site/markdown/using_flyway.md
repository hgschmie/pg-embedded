# Using Flyway for Database customization

`pg-embedded` offers the [FlywayPreparer](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/FlywayPreparer.html) that uses the [Flyway Database migration framework](https://flywaydb.org/) for DDL preparation and database migration.

```java
try (DatabaseManager manager = DatabaseManager.multiDatabases()
    .withInstancePreparer(EmbeddedPostgres.Builder::withDefaults)

    // apply all flyway migrations from the db/testing classpath location
    // to every database handed out by this manager
    .withDatabasePreparer(FlywayPreparer.forClasspathLocation("db/testing"))

    .build()
    .start()) {
    ...
}
```

The [FlywayPreparer](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/FlywayPreparer.html) is customizable by using the `FlywayPreparer.addCustomizer()` and `FlywayPreparer.addCustomizers()` methods which can augment the Flyway `FluentConfiguration` instance that is used to create the `Flyway` migrator.
