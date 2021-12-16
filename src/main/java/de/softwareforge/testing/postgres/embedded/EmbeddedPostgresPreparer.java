/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.softwareforge.testing.postgres.embedded;

import java.io.IOException;
import java.sql.SQLException;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Prepare a specific object instance. This allows user interaction to modify or tweak an instance before it is used.
 * <p>
 * Allows e.g. for preparation of data sources, postgres instances and other places where additional degrees of customization are needed.
 *
 * @param <T> The object type to be prepared.
 * @since 3.0
 */
@FunctionalInterface
public interface EmbeddedPostgresPreparer<T> {

    /**
     * Returns a typed instance of a no-op (do nothing) preparer.
     *
     * @param <U> The type to use.
     * @return a Do-nothing preparer.
     */
    static <U> EmbeddedPostgresPreparer<U> noOp() {
        return element -> {};
    }

    /**
     * Callback to customize a given object instance.
     *
     * @param element The instance. Must never be null. Any method on the builder can be called.
     * @throws SQLException For any SQL related problems.
     * @throws IOException  For any IO related problem.
     */
    void prepare(@NonNull T element) throws IOException, SQLException;
}
