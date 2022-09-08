SHELL = /bin/sh
.SUFFIXES:
.PHONY: install test deploy deploy-site help

# replace PG_MAVEN_OPTS with implicit MAVEN_OPTS, once 3.9.x or later has been released
MAVEN = ./mvnw ${PG_MAVEN_OPTS}

default: help

install:
	${MAVEN} clean install

test:
	${MAVEN} surefire:test

deploy:
	${MAVEN} clean deploy

# run install b/c https://issues.apache.org/jira/browse/MJAVADOC-701
deploy-site:
	${MAVEN} clean install site-deploy

help:
	@echo " * install     - installs basepom versions in the local maven repository"
	@echo " * deploy      - installs basepom versions in the snapshot OSS repository"
	@echo " * test        - run unit tests"
	@echo " * deploy-site - builds and deploys the documentation site"
	@echo " * release     - release a new version to maven central"
