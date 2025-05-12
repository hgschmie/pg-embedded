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

export MAVEN_OPTS
export MAVEN_ARGS

# must be the first target
default:: help

Makefile:: ;

clean::
	${MAVEN} clean

install::
	${MAVEN} clean install

# do not replace with "install-notests run-tests", the integration tests
# must be run right after the inline plugin; otherwise the project pom and
# not the newly created pom without inlined dependencies is used for the
# integration tests.
tests:: MAVEN_ARGS += -Dbasepom.it.skip=false
tests::
	${MAVEN} clean verify

install-notests:: MAVEN_ARGS += -Dbasepom.test.skip=true
install-notests:: install

install-fast:: MAVEN_ARGS += -Pfast
install-fast:: install

run-tests:: MAVEN_ARGS += -Dbasepom.it.skip=false
run-tests::
	${MAVEN} surefire:test invoker:install invoker:integration-test invoker:verify

deploy:: MAVEN_ARGS += -Dbasepom.it.skip=false
deploy::
	${MAVEN} clean deploy

deploy-site:: MAVEN_ARGS += -Dbasepom.it.skip=false
deploy-site:: install
	${MAVEN} site-deploy

release::
	${MAVEN} clean release:clean release:prepare release:perform

release-site:: MAVEN_ARGS += -Ppg-embedded-release
release-site:: deploy-site

help::
	@echo " * clean            - clean local build tree"
	@echo " * install          - installs build result in the local maven repository"
	@echo " * install-notests  - same as 'install', but skip unit tests"
	@echo " * install-fast     - same as 'install', but skip unit tests and static analysis"
	@echo " * tests            - build code and run unit and integration tests except really slow tests"
	@echo " * run-tests        - run all unit and integration tests except really slow tests"
	@echo " * deploy           - installs build result in the snapshot OSS repository"
	@echo " * deploy-site      - builds and deploys the documentation site"
	@echo " * release          - release a new version to maven central"
	@echo " * release-site     - build the release version of the documentation site"
