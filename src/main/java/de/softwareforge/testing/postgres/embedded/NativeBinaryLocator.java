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

import static com.google.common.base.Preconditions.checkState;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;
import javax.annotation.CheckForNull;

/**
 * Locates a native binary on the Filesystem. If necessary, it should download the binary first from the network.
 * <p>
 * Implementations of this class <b>must</b> implement {@link Object#hashCode()} and {@link Object#equals(Object)} and <i>should</i> implement {@link
 * Object#toString()}.
 *
 * @since 3.0
 */
public interface NativeBinaryLocator {

    String INSTALL_DIRECTORY_PREFIX = "bin-";

    /**
     * Returns an input stream from which the contents of the binary archive can be read.
     *
     * @return An input stream. May return null.
     * @throws IOException If the archive could not be located or read.
     */
    @CheckForNull
    InputStream getInputStream() throws IOException;

    /**
     * Returns a string identifier that represents the archive returned. This identifier should be stable (same value for the same archive), even across
     * multiple JVM invocations. The value must only contain characters that can be used as a legal file name.
     * <p>
     * The default implementation needs to read the full archive contents and is relatively slow. Implementations of this interface can override this method to
     * provide a faster way to create a stable identifier based on the specific implementation.
     * <p>
     * The default implementation hashes the archive contents and uses it to return a stable file name.
     *
     * @return A stable indentifier that can be used as a file name.
     * @throws IOException If the stream could not be read.
     */
    @Nonnull
    @SuppressWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE") // false positive
    default String getIdentifier() throws IOException {
        try (InputStream installationArchive = getInputStream()) {
            checkState(installationArchive != null, "Locator '%s' did not find a suitable archive to unpack!", toString());
            try (HashingInputStream hashStream = new HashingInputStream(Hashing.murmur3_128(), installationArchive)) {
                ByteStreams.exhaust(hashStream);
                return INSTALL_DIRECTORY_PREFIX + BaseEncoding.base16().encode(hashStream.hash().asBytes());
            }
        }
    }
}
