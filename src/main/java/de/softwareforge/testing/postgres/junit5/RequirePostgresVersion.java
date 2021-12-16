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
package de.softwareforge.testing.postgres.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * {@code RequirePostgresVersion} signals the annotated test class or method that it only should execute if the PostgreSQL server is within a specific range.
 * If the version is outside the range, the annotated test class or method is skipped.
 * <p>
 * PostgreSQL versions are interpreted as described in the <a href="https://www.postgresql.org/support/versioning/">Versioning Policy</a> and  structured as
 * "major.minor.patch". Versions can be abbreviated, e.g. "13" is interpreted as "13.0.0".
 * <p>
 * Using "0.0.0" or the empty string will skip the respective boundary check.
 * <p>
 *
 * @since 4.1
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith({PostgresVersionCondition.class})
public @interface RequirePostgresVersion {

    /**
     * Sets the minimum required version. This check is inclusive; the database must have at least the version specified.
     *
     * @return The minimum required version. Default is the empty string (ignore the check).
     */
    String atLeast() default "";

    /**
     * Sets the upper boundary version. The check is <b>exclusive</b>, the database version must be less than this version.
     *
     * @return The maximum allowed version. Default is the empty string (ignore the check).
     */
    String lessThan() default "";
}
