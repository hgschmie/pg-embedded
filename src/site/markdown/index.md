# Embedded PostgreSQL for Java

Start a PostgreSQL server for unit testing or local development.

This library controls PostgreSQL server instances and gives easy access to one or more databases on the database server. Using `pg-embedded` makes unit and integration tests simple and allows easy development without having to install local binaries.

## How it works

The library spins up one or more PostgreSQL servers up and controls their lifecycle through [EmbeddedPostgres](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.html) instances. When an instance is closed, the database server is shut down.

[EmbeddedPostgres](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.html) class instances manage a PostgreSQL server. Each server is independent, multiple instances will manage multiple, independent servers. This allows testing of more complex systems that may use multiple databases.

[DatabaseManager](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.html) provides an API to manage one or more database instances on a PostgreSQL server. Each [DatabaseManager](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.html) manages a PostgreSQL server.

pg-embedded supports the JUnit5 test framework directly through [EmbeddedPgExtension](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/junit5/EmbeddedPgExtension.html).

## How to use it

* [Using EmbeddedPostgres](using_embedded_postgres.html) to control PostgreSQL server instances.
* [Using DatabaseManager](using_database_manager.html) to manage databases.

## Testing with pg-embedded

* [Using pg-embedded with JUnit 5](junit5.html)


## Reference

* [Javadoc](apidocs/index.html)
* [Building the code](building.html)
* [Contributing](contributing.html)
