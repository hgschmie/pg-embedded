#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

SHELL = /bin/sh
.SUFFIXES:

MAVEN = ./mvnw

export MAVEN_OPTS MAVEN_CONFIG

# must be the first target
default:: help

Makefile:: ;

clean::
	${MAVEN} clean

install::
	${MAVEN} clean install

test::
	${MAVEN} surefire:test

deploy::
	${MAVEN} clean deploy

# run install b/c https://issues.apache.org/jira/browse/MJAVADOC-701
deploy-site::
	${MAVEN} clean install site-deploy

release::
	${MAVEN} clean release:clean release:prepare release:perform

release-site:: MAVEN_CONFIG += -Ppg-embedded-release
release-site:: deploy-site

help::
	@echo " * clean        - clean local build tree"
	@echo " * install      - installs build result in the local maven repository"
	@echo " * test         - run unit tests"
	@echo " * deploy       - installs build result in the snapshot OSS repository"
	@echo " * deploy-site  - builds and deploys the documentation site"
	@echo " * release      - release a new version to maven central"
	@echo " * release-site - build the release version of the documentation site"
