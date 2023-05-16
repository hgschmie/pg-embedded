# Setting the PostgreSQL version and architecture

By default, `pg-embedded` uses a PostgreSQL 13 database. This version can be changed in multiple ways:

- call `Builder.setServerVersion()` on the [EmbeddedPostgres.Builder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/EmbeddedPostgres.Builder.html)
- call `Builder.withInstancePreparer(b -> b.setServerVersion(...))` on the [DatabaseManagerBuilder](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/DatabaseManager.Builder.html)
- set the `pg-embedded.postgres-version` system property

Setting the system property overrides all other settings.

`pg-embedded` downloads repackaged [EnterpriseDB PostgreSQL](https://www.enterprisedb.com/download-postgresql-binaries) releases from the Maven repository system using the [io.zonky.test.postgres](https://search.maven.org/search?q=g:io.zonky.test.postgres) groupId. Any artifact available here can be used with `pg-embedded` as long as the version and binary architecture is supported.

* [More information about Zonky.IO PostgreSQL binaries](https://github.com/zonkyio/embedded-postgres-binaries).
* [List of all available PostgreSQL versions](https://mvnrepository.com/artifact/io.zonky.test.postgres/embedded-postgres-binaries-bom). Only 9.6 and later versions are supported by `pg-embedded`, older versions might still work but are unsupported (please do not file bug reports about older versions).

## OS, package and CPU architectures

There is a babylonian confusion for naming things with modern CPU architectures. the following things are all the same:

- `x86_64`, `amd64` - Intel based 64 bit architecture
- `aarch64`, `arm_64`, `arm64v8` - ARM based 64 bit architecture
- `aarch32`, `arm_32`, `arm32v7`, `arm` - ARM based 32 bit architecture

Java, the ZonkyIO binaries, postgres itself and the system OS use varying permutations of these identifiers.



For most operating systems, the system architecture and the architecture of the postgres distribution must match. However, modern MacOS systems can run both x86_64 and aarch64 binaries. Most of the recent ZonkyIO binaries contain both architectures ("fat binaries") so the actual architecture is chosen by the OS and the distribution architecture is not as important. ZonkyIO creates packages with the x86_64 and arm64v8 names, however the x86_64 variants are much more up-to-date. For that reason, `pg-embedded` prefers the x86_64 packages even on modern MacOS systems. In some rare cases, it might be beneficial to force the arm64v8 variant by setting the `pg-embedded.prefer-native` property to `true`.

*NOTE*: The [FlywayPreparer](apidocs/de.softwareforge.testing.postgres/de/softwareforge/testing/postgres/embedded/FlywayPreparer.html) only supports PostgreSQL 10+.

## Tested

| OS    | Architecture | Variant                               | Remarks                                                                                                                                          |
|-------|--------------|---------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| Linux | x86_64       | RHEL / CentOS                         |                                                                                                                                                  |
| Linux | x86_64       | Debian 11 (bullseye)                  | Needs `locales-all` installed for database locales to work                                                                                       |
| Linux | x86_64       | Alpine Linux                          |                                                                                                                                                  |
| Linux | x86_64       | Amazon Linux 2                        |                                                                                                                                                  |
| Linux | aarch64      | Amazon Linux 2                        | tested on AWS Graviton CPU                                                                                                                       |
| Linux | aarch64      | RockyLinux 8                          | tested on Raspberry Pi 4                                                                                                                         |
| Linux | aarch32      | CentOS 7                              | tested on Raspberry Pi 3B+                                                                                                                       |
| MacOS | x86_64       | MacOS 11.6+                           |                                                                                                                                                  |
| MacOS | aarch64      | MacOS 12.5.1 with Rosetta 2 installed | All x86_64 binaries work as well, recent binaries contain both x86_64 and aarch64 variants.                                                      |
| MacOS | aarch64      | MacOS 13.3.1 with Rosetta 2 installed | All x86_64 binaries work as well, recent binaries contain both x86_64 and aarch64 variants. Older x86_64 binaries (for 9.6 and 10) may not work. |
| MacOS | aarch64      | MacOS 12.5.1 without Rosetta 2        | Requires arm64v8 binaries or "fat" x86_64 binaries that also contain aarch64 variants.                                                           |

## Untested

| OS      | Architecture | Variant | Remarks                   |
|---------|--------------|---------|---------------------------|
| Windows | x86_64       | -       | untested, patches welcome |
| -       | i386         | -       | untested                  |
