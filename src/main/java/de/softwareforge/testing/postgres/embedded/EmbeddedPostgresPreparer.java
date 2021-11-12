package de.softwareforge.testing.postgres.embedded;

import java.io.IOException;
import java.sql.SQLException;

@FunctionalInterface
public interface EmbeddedPostgresCustomizer<T> {

    static <U> EmbeddedPostgresCustomizer<U> noOp() {
        return element -> {};
    }

    void customize(T element) throws IOException, SQLException;
}
