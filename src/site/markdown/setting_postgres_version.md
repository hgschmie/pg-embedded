# Setting the PostgreSQL version and architecture

By default, `pg-embedded` uses a PostgreSQL 13 database. This version can be changed in multiple ways:

- call `Builder.setServerVersion()` on the [EmbeddedPostgres.Builder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.Builder.html)
- call `Builder.withInstancePreparer(b -> b.setServerVersion(...))` on the [DatabaseManagerBuilder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.Builder.html)
- set the `pg-embedded.postgres-version` system property

Setting the system property overrides all other settings.

`pg-embedded` downloads repackaged [EnterpriseDB PostgreSQL](https://www.enterprisedb.com/download-postgresql-binaries) releases from the Maven repository system using the [io.zonky.test.postgres](https://search.maven.org/search?q=g:io.zonky.test.postgres) groupId. Any artifact available here can be used with `pg-embedded` as long as the version and binary architecture is supported.

* [More information about Zonky.IO PostgreSQL binaries](https://github.com/zonkyio/embedded-postgres-binaries).
* [List of all available PostgreSQL versions](https://mvnrepository.com/artifact/io.zonky.test.postgres/embedded-postgres-binaries-bom). Only 9.6 and later versions are supported by `pg-embedded`, older versions might still work but are unsupported (please do not file bug reports about older versions).

For most operating systems, the system architecture and the architecture of the postgres distribution must be the same. However, on modern MacOS systems, it is possible to also run older (`x86_64`) binaries while the system is
`arm64` based. For older versions of postgres, it may be necessary to force using these `x86_64` binaries instead of native by setting the `pg-embedded.prefer-native` property to `false`.


The [FlywayPreparer](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/FlywayPreparer.html) only supports PostgreSQL 10+.

## Tested

| OS    | Architecture | Variant                               | Remarks                                                                                     |
|-------|--------------|---------------------------------------|---------------------------------------------------------------------------------------------|
| Linux | x86_64       | RHEL / CentOS                         |                                                                                             |
| Linux | x86_64       | Debian 11 (bullseye)                  | Needs `locales-all` installed for database locales to work                                  |
| Linux | x86_64       | Alpine Linux                          |                                                                                             |
| Linux | x86_64       | Amazon Linux 2                        |                                                                                             |
| Linux | aarch64      | Amazon Linux 2                        | tested on AWS Graviton CPU                                                                  |
| Linux | aarch64      | RockyLinux 8                          | tested on Raspberry Pi 4                                                                    |
| Linux | aarch32      | CentOS 7                              | tested on Raspberry Pi 3B+                                                                  |
| MacOS | x86_64       | MacOS 11.6+                           |                                                                                             |
| MacOS | aarch64      | MacOS 12.5.1 with Rosetta 2 installed | All x86_64 binaries work as well, recent binaries contain both x86_64 and aarch64 variants. |
| MacOS | aarch64      | MacOS 12.5.1 without Rosetta 2        | Requires "fat" binaries that contain aarch64 variants.                                      |

## Untested

| OS      | Architecture | Variant | Remarks                   |
|---------|--------------|---------|---------------------------|
| Windows | x86_64       | -       | untested, patches welcome |
| -       | i386         | -       | untested                  |
