# Setting the PostgreSQL version

By default, `pg-embedded` uses a PostgreSQL 13 database. This version can be changed in multiple ways:

- call `Builder.setServerVersion()` on the [EmbeddedPostgres.Builder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.Builder.html)
- call `Builder.withInstancePreparer(b -> b.setServerVersion(...))` on the [DatabaseManagerBuilder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.Builder.html)
- set the `pg-embedded.postgres-version` system property

Setting the system property overrides all other settings.

`pg-embedded` looks for packaged PostgreSQL releases on the Maven repository system in the [io.zonky.test.postgres](https://search.maven.org/search?q=g:io.zonky.test.postgres) group. Any artifact stored here can be used with `pg-embedded` as long as the version and binary architecture is supported.

Any PostgreSQL version 9.6+ is supported. The [FlywayPreparer](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/FlywayPreparer.html) does not support PostgreSQL 9.x.

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
