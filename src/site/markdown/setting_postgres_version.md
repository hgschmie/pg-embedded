# Setting the PostgreSQL version

By default, `pg-embedded` uses a PostgreSQL 13 database. This version can be changed in multiple ways:

- call `Builder.setServerVersion()` on the [EmbeddedPostgres.Builder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.Builder.html)
- call `Builder.withInstancePreparer(b -> b.setServerVersion(...))` on the [DatabaseManagerBuilder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.Builder.html)
- set the `pg-embedded.postgres-version` system property

Setting the system property overrides all other settings.

`pg-embedded` downloads repackaged [EnterpriseDB PostgreSQL](https://www.enterprisedb.com/download-postgresql-binaries) releases from the Maven repository system using the [io.zonky.test.postgres](https://search.maven.org/search?q=g:io.zonky.test.postgres) groupId. Any artifact available here can be used with `pg-embedded` as long as the version and binary architecture is supported.

* [More information about Zonky.IO PostgreSQL binaries](https://github.com/zonkyio/embedded-postgres-binaries).
* [List of all available PostgreSQL versions](https://mvnrepository.com/artifact/io.zonky.test.postgres/embedded-postgres-binaries-bom). Only 9.6 and later versions are supported by `pg-embedded`, older versions might still work but are unsupported (please do not file bug reports about older versions).

The [FlywayPreparer](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/FlywayPreparer.html) only supports PostgreSQL 10+.

## Tested

| OS | Architecture | Variant | Remarks |
|----|--------------|--------------|---------|
| Linux | x86_64 | RHEL / CentOS   |         |
| Linux | x86_64 | Debian 11 (bullseye) | Needs `locales-all` installed for database locales to work |
| Linux | x86_64 | Alpine Linux | |
| Linux | x86_64 | Amazon Linux 2  |         |
| Linux | aarch64 | Amazon Linux 2 | Graviton CPU |
| Linux | aarch32 | CentOS 7 | Raspberry Pi 3B+ |
| MacOS | x86_64 | MacOS 11.6 | |

## Untested

| OS | Architecture | Variant | Remarks |
|----|--------------|--------------|---------|
| MacOS | aarch64 | (fat)  | the PostgreSQL binaries used do not yet support aarch64 natively, so the x86_64 binaries are used and executed through Rosetta. When "fat" binaries are available, they should be picked up and just work. |
| MacOS | aarch64 | (native) | When native aarch64 binaries are available, set the `pg-embedded.prefer-native` system property to `true` will enable pg-embedded to use those directly. |
| Windows | x86_64 | - | untested, patches welcome |
| - | i386 | - | untested |
