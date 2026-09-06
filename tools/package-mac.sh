#!/usr/bin/env bash
#
# Builds RamWatch.app, so macOS shows the app under its own name and icon.
#
# Run straight from a JVM the app is just a "java" process: that is the name the menu bar and
# the dock read, and the icon that goes with it is the generic one. macOS takes both from the
# bundle around the app, which is what jpackage builds here.
#
# Usage: tools/package-mac.sh   (the bundle lands in target/dist/RamWatch.app)
set -euo pipefail

cd "$(dirname "$0")/.."
root=$(pwd)
name=RamWatch
work=target/packaging

command -v jpackage >/dev/null || { echo "jpackage not found on PATH (needs a JDK 17 or newer)"; exit 1; }

# 1. The app and its dependencies, gathered in one directory.
mvn -q package -DskipTests
mvn -q dependency:copy-dependencies -DoutputDirectory="$root/$work/lib" -DincludeScope=runtime
jar=$(basename "$(ls target/*.jar | head -1)")
cp "target/$jar" "$work/lib/"

# 2. The icon, at every size macOS asks for.
rm -rf "$work/$name.iconset"
mkdir -p "$work/$name.iconset"
for size in 16 32 128 256 512; do
    java -Djava.awt.headless=true tools/IconGenerator.java "$size" "$work/$name.iconset/icon_${size}x${size}.png" >/dev/null
    java -Djava.awt.headless=true tools/IconGenerator.java "$((size * 2))" "$work/$name.iconset/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns "$work/$name.iconset" -o "$work/$name.icns"

# 3. The bundle itself.
rm -rf "target/dist/$name.app"
mkdir -p target/dist
jpackage \
    --type app-image \
    --name "$name" \
    --app-version 1.0.0 \
    --input "$work/lib" \
    --main-jar "$jar" \
    --main-class com.ramwatch.Launcher \
    --icon "$work/$name.icns" \
    --dest target/dist \
    --mac-package-identifier com.ramwatch.app

echo "built target/dist/$name.app — open it with: open target/dist/$name.app"
