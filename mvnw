#!/bin/sh
# Maven launcher: uses installed "mvn" if present, otherwise downloads Maven 3.9.11 once.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
MVN_VER=3.9.11
BASE="$HOME/.m2/wrapper/dists/apache-maven-$MVN_VER"
MVN_HOME="$BASE/apache-maven-$MVN_VER"

if [ ! -x "$MVN_HOME/bin/mvn" ]; then
  URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MVN_VER/apache-maven-$MVN_VER-bin.tar.gz"
  echo "Downloading Maven $MVN_VER (first run only)..."
  mkdir -p "$BASE"
  TMP="$BASE/maven.tar.gz"
  if command -v curl >/dev/null 2>&1; then curl -fsSL "$URL" -o "$TMP"
  elif command -v wget >/dev/null 2>&1; then wget -q "$URL" -O "$TMP"
  else echo "ERROR: need curl or wget to download Maven." >&2; exit 1; fi
  tar -xzf "$TMP" -C "$BASE"
  rm -f "$TMP"
fi
exec "$MVN_HOME/bin/mvn" "$@"
