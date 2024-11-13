#!/usr/bin/env bash
# We need to build bindings for the engines so we can use them in Java
if [ ! -d "engines" ]; then mkdir engines; fi
cd "engines" || exit 1
function jextract_get() {
  if [ ! -d "jextract-22" ]; then
    curl -L "https://download.java.net/java/early_access/jextract/22/5/openjdk-22-jextract+5-33_macos-x64_bin.tar.gz" -o jextract.tar.gz
    tar xf jextract.tar.gz
    rm jextract.tar.gz
  fi
  PATH="$(pwd)/jextract-22/bin:${PATH}"
}
function git_latest_tag() {
  ### Checkout the latest tag
  local tag
  tag="$(git describe origin/master --abbrev=0 --tags)"
  git checkout "${tag}"
}

function prime_server() {
  if [ ! -d prime_server ]; then git clone https://github.com/kevinkreiser/prime_server.git ; else git -C prime_server remote update; fi
  cd prime_server || exit 1
  # latest tag (0.7.0) is years old
  git checkout master && git pull
  #git_latest_tag
  git submodule update --init --recursive
  ./autogen.sh
  ./configure
  make test -j8
  #sudo make install
  cd ..
}

function valhalla() {
  ## Valhalla
  ### Clone valhalla
  if [ ! -d "valhalla" ]; then git clone https://github.com/valhalla/valhalla.git; else git -C valhalla remote update; fi
  cd "valhalla" || exit 1
  git checkout master && git pull
  git checkout 3.5.1
  npm install --ignore-scripts
  git submodule update --init --recursive
  if [ -d build ] ; then rm -rf build; fi
  mkdir build
  cd build || exit 1
  cmake .. -DCMAKE_BUILD_TYPE=Release -DDENABLE_STATIC_LIBRARY_MODULES=On -DDENABLE_GDAL=OFF -DENABLE_SERVICES=OFF
  make -j"$(nproc)"
  make package
  #sudo make install
  cd ../../../

  mkdir -p valhalla_tiles
  ./engines/valhalla/build/valhalla_build_config --mjolnir-tile-dir $(pwd)/valhalla_tiles --mjolnir-tile-extract $(pwd)/valhalla_tiles.tar --mjolnir-timezone $(pwd)/valhalla_tiles/timezones.sqlite --mjolnir-admin $(pwd)/valhalla_tiles/admins.sqlite > valhalla.json
  ./engines/valhalla/build/valhalla_build_timezones > valhalla_tiles/timezones.sqlite
  ./engines/valhalla/build/valhalla_build_admins -c valhalla.json ~/Downloads/colorado-latest.osm.pbf
  ./engines/valhalla/build/valhalla_build_tiles -c valhalla.json ~/Downloads/colorado-latest.osm.pbf
  ./engines/valhalla/build/valhalla_build_extract -c valhalla.json -v --overwrite
  # Test install
  ./engines/valhalla/build/valhalla_run_route --config valhalla.json --json '{"locations": [{"lat":39.0776524, "lon":-108.4588285}, {"lat":39.0676135, "lon":-108.5601538}], "costing":"auto","directions_options":{"units":"miles"}}' --verbose-lanes
  ./engines/valhalla/build/valhalla_service valhalla.json route '{"locations": [{"lat":39.0776524, "lon":-108.4588285}, {"lat":39.0676135, "lon":-108.5601538}], "costing":"auto","directions_options":{"units":"miles"}}'

  # Note: future versions of jextract *may* be able to take multiple header files. TODO change trailing \; to \+
  # Important file is tyr/actor.h anyway.
  find engines/valhalla/valhalla -name '*.h' -exec jextract \
    --include-dir /Library/Developer/CommandLineTools/SDKs/MacOSX14.4.sdk/usr/include/c++/v1 \
    --target-package org.openstreetmap.josm.plugins.routing2.lib.valhalla \
    --output src/main/java \
    {} \;

}
jextract_get
prime_server
set -ex
valhalla

