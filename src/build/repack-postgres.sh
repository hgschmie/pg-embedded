#!/bin/bash -ex
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# NB: This is the *server* version, which is not to be confused with the client library version.
# The important compatibility point is the *protocol* version, which hasn't changed in ages.

if [ $# != 1 ]; then
    echo "No PostgreSQL version given"
    exit 1
fi

VERSION=$1; shift

RSRC_DIR=$PWD/target/generated-resources

[ -e $RSRC_DIR/.repacked ] && echo "Already repacked, skipping..." && exit 0

PACKDIR=$(mktemp -d -t wat.XXXXXX)
LINUX_DIST=dist/postgresql-$VERSION-linux-x64-binaries.tar.gz
OSX_DIST=dist/postgresql-$VERSION-osx-binaries.zip
WINDOWS_DIST=dist/postgresql-$VERSION-win-binaries.zip

#
# from https://www.enterprisedb.com/download-postgresql-binaries
#
mkdir -p dist/ target/generated-resources/
[ -e $LINUX_DIST ] || curl -L -o $LINUX_DIST "https://get.enterprisedb.com/postgresql/postgresql-$VERSION-linux-x64-binaries.tar.gz"
[ -e $OSX_DIST ] || curl -L -o $OSX_DIST "https://get.enterprisedb.com/postgresql/postgresql-$VERSION-osx-binaries.zip"
[ -e $WINDOWS_DIST ] || curl -L -o $WINDOWS_DIST "https://get.enterprisedb.com/postgresql/postgresql-$VERSION-windows-x64-binaries.zip"

tar xzf $LINUX_DIST -C $PACKDIR
pushd $PACKDIR/pgsql
tar cJf $RSRC_DIR/postgresql-Linux-x86_64.txz \
  share/postgresql \
  lib \
  bin/initdb \
  bin/pg_ctl \
  bin/postgres
popd

rm -fr $PACKDIR && mkdir -p $PACKDIR

unzip -q -d $PACKDIR $OSX_DIST
pushd $PACKDIR/pgsql
tar cJf $RSRC_DIR/postgresql-Darwin-x86_64.txz \
  share/postgresql \
  lib \
  bin/initdb \
  bin/pg_ctl \
  bin/postgres
popd

rm -fr $PACKDIR && mkdir -p $PACKDIR

unzip -q -d $PACKDIR $WINDOWS_DIST
pushd $PACKDIR/pgsql
tar cJf $RSRC_DIR/postgresql-Windows-x86_64.txz \
  share \
  lib \
  bin/initdb.exe \
  bin/pg_ctl.exe \
  bin/postgres.exe \
  bin/*.dll
popd

rm -rf $PACKDIR
touch $RSRC_DIR/.repacked
