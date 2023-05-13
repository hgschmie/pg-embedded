SHELL = /bin/sh
.SUFFIXES:
.PHONY: help clean install test deploy deploy-site release

MAVEN = ./mvnw

default: help

clean:
	${MAVEN} clean

install:
	${MAVEN} clean install

test:
	${MAVEN} surefire:test

deploy:
	${MAVEN} clean deploy

# run install b/c https://issues.apache.org/jira/browse/MJAVADOC-701
deploy-site:
	${MAVEN} clean install site-deploy

release:
	${MAVEN} clean release:clean release:prepare release:perform

help:
	@echo " * clean       - clean local build tree"
	@echo " * install     - installs build result in the local maven repository"
	@echo " * deploy      - installs build result in the snapshot OSS repository"
	@echo " * test        - run unit tests"
	@echo " * deploy-site - builds and deploys the documentation site"
	@echo " * release     - release a new version to maven central"
