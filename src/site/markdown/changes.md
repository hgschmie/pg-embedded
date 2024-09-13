# Change Log

This is the change log for pg-embedded. It follows [Keep a Changelog v1.0.0](http://keepachangelog.com/en/1.0.0/).

## 5.2.0 - 2024-09-12

- dropped support for Postgres 11 (obsolete by postgresql.org)
- dependency updates, build fixes
- tests are now run against 16.4.0 (used to be 15.4.0)
- Default embedded database version is now 15 (used to be 13)


## 5.1.0 - 2023-10-22

- dropped support for Postgres 9.x and 10 (obsolete by postgresql.org)
- dependency updates, build fixes
- switch to three digit versioning


## 5.0 - 2023-05-16

### Removed

- remove deprecated `DatabasePreparer` code and all related methods
- remove deprecated `EmbeddedPostgres.BuilderCustomizer` code
- remove deprecated `EmbeddedPostgres#addLocaleConfiguration()` method
- remove deprecated `FlywayPreparer#customize()` method

### Changed

- fixed obscure NPE when postgres does not start
- MacOS default is now to use native binaries, not x86_64
- update maven loader to 2.1.0

## 4.3 - 2023-05-14

### Added

- log database architecture when starting.
- support mac arm64 for building the distribution.


- update github actions
- update included wrapper to Maven 3.9.1

- use guava 31.1-jre
- use slf4j 1.7.36
- baseline testing now uses postgres 14.7

- fix some corner cases where directory extraction failed because of permission problems

## 4.2 2022-02-23

## 4.1 2021-12-15

## 4.0 2021-11-30

## 3.0 2021-10-20

* First public release
