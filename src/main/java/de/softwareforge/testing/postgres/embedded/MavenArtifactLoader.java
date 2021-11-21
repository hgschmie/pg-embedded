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
import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.locator.ServiceLocator;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MavenArtifactLoader {

    private static final Logger LOG = LoggerFactory.getLogger(MavenArtifactLoader.class);

    private static final String USER_HOME = System.getProperty("user.home");
    private static final File USER_MAVEN_HOME = new File(USER_HOME, ".m2");
    private static final String ENV_M2_HOME = System.getenv("M2_HOME");

    private static final File DEFAULT_USER_SETTINGS_FILE = new File(USER_MAVEN_HOME, "settings.xml");
    private static final File DEFAULT_USER_REPOSITORY = new File(USER_MAVEN_HOME, "repository");
    private static final File DEFAULT_GLOBAL_SETTINGS_FILE =
            new File(System.getProperty("maven.home", Objects.requireNonNullElse(ENV_M2_HOME, "")), "conf/settings.xml");

    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession mavenSession;
    private final List<RemoteRepository> remoteRepositories;

    MavenArtifactLoader() {
        @SuppressWarnings("deprecation")
        ServiceLocator serviceLocator = createServiceLocator();
        this.repositorySystem = serviceLocator.getService(RepositorySystem.class);

        try {
            Settings settings = createSettings();
            File localRepositoryLocation = settings.getLocalRepository() != null ? new File(settings.getLocalRepository()) : DEFAULT_USER_REPOSITORY;
            LocalRepository localRepository = new LocalRepository(localRepositoryLocation);
            this.remoteRepositories = extractRemoteRepositories(settings);

            DefaultRepositorySystemSession mavenSession = MavenRepositorySystemUtils.newSession();

            this.mavenSession = mavenSession.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(mavenSession, localRepository));


        } catch (SettingsBuildingException e) {
            throw new IllegalStateException("Could not load maven settings:", e);
        }
    }

    File getArtifactFile(String groupId, String artifactId, String version) throws IOException {

        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(new DefaultArtifact(groupId, artifactId, "jar", version));
        artifactRequest.setRepositories(this.remoteRepositories);
        try {
            ArtifactResult artifactResult = this.repositorySystem.resolveArtifact(mavenSession, artifactRequest);
            Artifact artifact = artifactResult.getArtifact();
            return artifact.getFile();
        } catch (RepositoryException e) {
            throw new IOException(e);
        }
    }

    String findLatestVersion(String groupId, String artifactId, String version) throws IOException {

        Artifact artifact = new DefaultArtifact(groupId, artifactId, "jar", "[0,)");

        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact(artifact);
        rangeRequest.setRepositories(this.remoteRepositories);

        try {
            VersionRangeResult rangeResult = this.repositorySystem.resolveVersionRange(mavenSession, rangeRequest);
            ImmutableSortedSet.Builder<Version> builder = ImmutableSortedSet.reverseOrder();
            List<Version> artifactVersions = rangeResult.getVersions();
            if (artifactVersions != null) {
                for (Version artifactVersion : artifactVersions) {
                    if (artifactVersion.toString().equals(version) || artifactVersion.toString().startsWith(version + '.')) {
                        builder.add(artifactVersion);
                    }
                }
            }
            ImmutableSortedSet<Version> candiates = builder.build();
            checkState(!candiates.isEmpty(), "No suitable candidate for %s:%s:%s found!", groupId, artifactId, version);
            return candiates.first().toString();
        } catch (VersionRangeResolutionException e) {
            throw new IOException(format("Could not resolve version range: %s", rangeRequest), e);
        }
    }

    @SuppressWarnings("deprecation")
    private static ServiceLocator createServiceLocator() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();

        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable e) {
                LOG.error(format("Could not create instance of %s (implementation %s): ", type.getSimpleName(), impl.getSimpleName()), e);
            }
        });

        return locator;
    }

    private static Settings createSettings() throws SettingsBuildingException {
        SettingsBuildingRequest settingsBuildingRequest = new DefaultSettingsBuildingRequest()
                .setSystemProperties(System.getProperties())
                .setUserSettingsFile(DEFAULT_USER_SETTINGS_FILE)
                .setGlobalSettingsFile(DEFAULT_GLOBAL_SETTINGS_FILE);

        DefaultSettingsBuilderFactory settingBuilderFactory = new DefaultSettingsBuilderFactory();
        DefaultSettingsBuilder settingsBuilder = settingBuilderFactory.newInstance();
        SettingsBuildingResult settingsBuildingResult = settingsBuilder.build(settingsBuildingRequest);

        return settingsBuildingResult.getEffectiveSettings();
    }

    private static List<RemoteRepository> extractRemoteRepositories(Settings settings) {
        Map<String, Profile> profiles = settings.getProfilesAsMap();
        ImmutableList.Builder<RemoteRepository> builder = ImmutableList.builder();

        for (String profileName : settings.getActiveProfiles()) {
            Profile profile = profiles.get(profileName);
            if (profile != null) {
                List<Repository> repositories = profile.getRepositories();
                if (repositories != null) {
                    for (Repository repo : repositories) {
                        builder.add(new RemoteRepository.Builder(repo.getId(), "default", repo.getUrl()).build());
                    }
                }
            }
        }

        return builder.build();
    }
}
