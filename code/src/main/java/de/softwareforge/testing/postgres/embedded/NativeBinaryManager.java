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

import jakarta.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

/**
 * Returns an installation location for a native binary. The implementation is responsible for locating and optionally unpacking installing the binary.
 *
 * @since 3.0
 */
@FunctionalInterface
public interface NativeBinaryManager {

    /**
     * Returns the location (installation directory) for the installed binary.
     *
     * @return Installation directory with the native binary installed.
     * @throws IOException If the binary could not be located or installed.
     */
    @Nonnull
    File getLocation() throws IOException;

    /**
     * Sets the installation directory. This method must be called before the first call to {@link NativeBinaryManager#getLocation()}.
     * <p>
     * Implementing this method is optional if no local installation directory is required.
     *
     * @param installationBaseDirectory Base directory in which the binary distribution is unpacked. Must not be null.
     */
    default void setInstallationBaseDirectory(File installationBaseDirectory) {
    }
}
