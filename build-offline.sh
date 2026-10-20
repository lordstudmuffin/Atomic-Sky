#!/bin/sh
# Builds atomicsky with plain javac against compiled-stubs-1.21.1.jar (exact Minecraft 1.21.1 + NeoForge 21.1 signatures)
# plus the real ProtoManly's Weather jar (compile-only, never packaged). With internet access, use Gradle instead.
set -e
cd "$(dirname "$0")"
STUBS="${STUBS:-../compiled-stubs-1.21.1.jar}"
PMWEATHER="${PMWEATHER:-../pmweather-1.21.1-0.17.16-alpha.jar}"
STUB_OVERLAY="build/offline/stubfix"  # compile-only shims (Mixin annotations, ModConfigSpec builder); never packaged
VERSION=$(grep "^mod_version=" gradle.properties | cut -d= -f2)
rm -rf build/offline && mkdir -p build/offline/classes build/offline/stubfix build/libs
javac --release 21 -nowarn -Xlint:-options -proc:none -cp "$STUBS" -d "$STUB_OVERLAY" $(find stubfix -name "*.java")
javac --release 21 -nowarn -Xlint:-options -proc:none -cp "$STUB_OVERLAY:$STUBS:$PMWEATHER" -d build/offline/classes $(find src/main/java -name '*.java')
cp -r src/main/resources/. build/offline/classes/ && sed -i "s/\${mod_version}/$VERSION/" build/offline/classes/META-INF/neoforge.mods.toml
printf 'Manifest-Version: 1.0\nImplementation-Title: atomicsky\nImplementation-Version: %s\nMixinConfigs: atomicsky.mixins.json\n' "$VERSION" > build/offline/MANIFEST.MF
jar --create --file "build/libs/atomicsky-$VERSION.jar" --manifest build/offline/MANIFEST.MF -C build/offline/classes .
echo "Built build/libs/atomicsky-$VERSION.jar"
