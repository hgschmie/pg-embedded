# JUnit 5 Test Framework integration

`pg-embedded` supports the [JUnit 5](https://junit.org/) framework through the [EmbeddedPgExtension](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/junit5/EmbeddedPgExtension.html) class.


The following snipped creates a new extension programmatically:

```java
@RegisterExtension
public static EmbeddedPgExtension multiDatabase = MultiDatabaseBuilder.instanceWithDefaults().build();
```

[EmbeddedPgExtension](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/junit5/EmbeddedPgExtension.html) supports registration as a static field or an instance field. If registered as a static field, the [EmbeddedPostgres](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.html) instance supporting the extension is started once per test class, otherwise a new instance is started and stopped for each test method.


## Creating and customizing EmbeddedPgExtension

Programmatically created instances are the preferred way to use the [EmbeddedPgExtension](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/junit5/EmbeddedPgExtension.html). They can be customized extensively using the builder classes:

* [MultiDatabaseBuilder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/junit5/MultiDatabaseBuilder.html) creates an extension in **multi database** mode. Any invocation of `EmbeddedPgExtension.createDatabaseInfo()` or `EmbeddedPgExtension.createDataSource()` creates a new database.
* [SingleDatabaseBuilder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/junit5/SingleDatabaseBuilder.html) creates an extension in **single database** mode. Any invocation of `EmbeddedPgExtension.createDatabaseInfo()` or `EmbeddedPgExtension.createDataSource()` accesses the default database.

All builders have the same set static creation methods for simple, fluent configuration in test classes:

* `instance()` returns a vanilla, not customized [Builder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.Builder.html).
* `instanceWithDefaults()` returns a [Builder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.Builder.html) with `Builder.withInstancePreparer(EmbeddedPostgres.Builder::withDefaults)` as an instance preparer.
* `preparedInstance()` returns a [Builder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.Builder.html) that registers the provided [EmbeddedPostgresPreparer](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgresPreparer.html) as a database preparer
* `preparedInstanceWithDefaults()` returns a [Builder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.Builder.html) that registers the provided [EmbeddedPostgresPreparer](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgresPreparer.html) as a database preparer and `Builder.withInstancePreparer(EmbeddedPostgres.Builder::withDefaults)` as an instance preparer.

The methods to customize the [DatabaseManager](using_database_manager.html#Customizing_the_DatabaseManager) are also available for [EmbeddedPgExtension](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/junit5/EmbeddedPgExtension.html) customization.

## Declarative use

Tests can also use declarative registration of the [EmbeddedPgExtension](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/junit5/EmbeddedPgExtension.html) class.

```java
@ExtendWith(EmbeddedPgExtension.class)
public class DeclarativeTest {

    @Test
    public void testCode(DataSource dataSource) {
      ...
    }
```

Using the `@ExtendWith` annotation on the class declaration creates a per-class extension, registration on the method-level creates a per-method extension.

Declarative registration is equivalent to registering `MultiDatabaseBuilder.instanceWithDefaults()` programmatically.

## Parameter resolution

When using declarative registration (and unambiguous programmatic registration), test methods can declare parameters which will be resolved at runtime.

The [EmbeddedPgExtension](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/junit5/EmbeddedPgExtension.html) supports the following parameter types:

* [EmbeddedPostgres](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.html) instance used by the extension
* [DatabaseInfo](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseInfo.html), the value is equivalent to calling `EmbeddedPgExtension.createDatabaseInfo()`
* `DataSource`, the value is equivalent to calling `EmbeddedPgExtension.createDataSource()`.
