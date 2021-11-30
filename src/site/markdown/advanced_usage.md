# Advanced Usage


## Use a local PostgreSQL installation

Normally, the PostgreSQL binary is downloaded and started automatically. To use a special version of PostgreSQL that is not available as an artifact or when running on an unsupported architecture, a locally installed PostgreSQL can be used.

```java
try (EmbeddedPostgres pg = EmbeddedPostgres.builderWithDefaults()
        .useLocalPostgresInstallation("/usr/local")
        .build();
        Connection c = pg.createDefaultDataSource().getConnection();
        Statement s = c.createStatement()) {
    try (ResultSet rs = s.executeQuery("SELECT 1")) {
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        assertFalse(rs.next());
    }
}
```

This code uses a locally installed (in `/usr/local`) PostgreSQL to start and stop the managed PostgreSQL instances.

Note that for some operating systems (namely Linux), the locally installed PostgreSQL is supposed to be run under a special user (usually `postgres`) and, by default, tries to write to directories that are not world-writable (the `pg-embedded` PostgreSQL instances are run as the current user). In that case, it may be necessary to provide additional server configuration settings:

```java
try (EmbeddedPostgres pg = EmbeddedPostgres.builderWithDefaults()
        .useLocalPostgresInstallation("/usr")
        // open unix domain sockets in a world-writable directory
        .addServerConfiguration("unix_socket_directories", System.getProperty("java.io.tmpdir"))
        .build()) {
...
}
```

## Using a different NativeBinaryManager or NativeBinaryLocator

`pg-embedded` uses repackaged EnterpriseDB releases that are available through the Maven repository system. These releases are packaged as `tar.xz` archives.

To use different binaries, it is possible to replace the native binary manager:

```java
try (EmbeddedPostgres pg = EmbeddedPostgres.builderWithDefaults()
        .setNativeBinaryManager(new CustomBinaryManager())
        .build()) {
...
}
```

The native binary manager is responsible for locating the PostgreSQL distribution and unpacking it into a local directory. The `NativeBinaryManager.getLocation()` method must return the installation location.


It is also possible to only replace the location mechanism, e.g. to load the binary from the classpath:

```java
try (EmbeddedPostgres pg = EmbeddedPostgres.builderWithDefaults()
        .setNativeBinaryManager(new TarXzCompressedBinaryManager(new ClasspathLocator()))
        .build()) {
...
}

static class ClasspathLocator implements NativeBinaryLocator {
    @Override
    public InputStream getInputStream() {
        // MacOS specific
        return EmbeddedPostgres.class.getResourceAsStream("/postgres-darwin-x86_64.txz");
    }
}
```
