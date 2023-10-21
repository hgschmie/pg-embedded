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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import de.softwareforge.testing.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.util.List;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ComparisonChain;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * {@link ExecutionCondition} for {@link RequirePostgresVersion}.
 *
 * @see RequirePostgresVersion
 * @since 4.1
 */
public final class PostgresVersionCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return AnnotationSupport.findAnnotation(context.getElement(), RequirePostgresVersion.class)
                .map(this::checkPostgresVersion)
                .orElse(ConditionEvaluationResult.enabled("No version annotation found"));

    }

    private ConditionEvaluationResult checkPostgresVersion(RequirePostgresVersion requirePostgresVersion) {
        Version atLeastVersion = Version.valueOf(requirePostgresVersion.atLeast());
        Version lessThanVersion = Version.valueOf(requirePostgresVersion.lessThan());

        if (atLeastVersion.ignore() && lessThanVersion.ignore()) {
            return ConditionEvaluationResult.enabled("No PostgreSQL version range set");
        }

        try (EmbeddedPostgres pg = EmbeddedPostgres.forVersionCheck()) {
            Version postgresVersion = Version.valueOf(pg.getPostgresVersion());

            if (!atLeastVersion.ignore() && postgresVersion.compareTo(atLeastVersion) < 0) {
                return ConditionEvaluationResult.disabled(
                        format("Located PostgreSQL version is %s, at least version %s is required", postgresVersion, atLeastVersion));
            }

            if (!lessThanVersion.ignore() && lessThanVersion.compareTo(postgresVersion) < 0) {
                return ConditionEvaluationResult.disabled(
                        format("Located PostgreSQL version is %s, must be less than %s", postgresVersion, lessThanVersion));
            }

            return ConditionEvaluationResult.enabled(
                    format("Located PostgreSQL version is %s, version range is %s - %s", postgresVersion, atLeastVersion, lessThanVersion));

        } catch (IOException e) {
            return ConditionEvaluationResult.disabled("IOException while checking postgres version", e.getMessage());
        }
    }

    @AutoValue
    abstract static class Version implements Comparable<Version> {

        abstract int major();

        abstract int minor();

        abstract int patch();

        private static Version valueOf(String value) {
            checkNotNull(value, "value is null");

            List<String> values = Splitter.on('.').trimResults().splitToList(value);
            return new AutoValue_PostgresVersionCondition_Version(parseValue(values, 0),
                    parseValue(values, 1),
                    parseValue(values, 2));
        }

        private static int parseValue(List<String> values, int pos) {
            if (values.size() > pos && !values.get(pos).isEmpty()) {
                try {
                    return Integer.parseInt(values.get(pos));
                } catch (NumberFormatException e) {
                    return 0;
                }
            } else {
                return 0;
            }
        }

        private boolean ignore() {
            return major() == 0 && minor() == 0 && patch() == 0;
        }

        @Override
        public int compareTo(Version other) {
            return ComparisonChain.start()
                    .compare(major(), other.major())
                    .compare(minor(), other.minor())
                    .compare(patch(), other.patch())
                    .result();
        }

        @Override
        public String toString() {
            if (ignore()) {
                return "";
            } else {
                return Joiner.on('.').join(major(), minor(), patch());
            }
        }
    }
}
