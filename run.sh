#!/bin/bash
set -euo pipefail

# Launch the TokiFactor desktop app from the packaged fat jar.
#
# Gradle only runs when the jar is missing or older than the Kotlin sources. It
# runs with --no-daemon so no Gradle/Kotlin daemon is left behind afterwards.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_DIR="$REPO_ROOT/desktopApp/build/compose/jars"

# Inputs whose modification invalidates the packaged jar.
WATCHED=(
    "$REPO_ROOT/shared/src"
    "$REPO_ROOT/desktopApp/src/main"
    "$REPO_ROOT/build.gradle.kts"
    "$REPO_ROOT/settings.gradle.kts"
    "$REPO_ROOT/gradle.properties"
    "$REPO_ROOT/gradle/libs.versions.toml"
    "$REPO_ROOT/gradle/wrapper/gradle-wrapper.properties"
    "$REPO_ROOT/shared/build.gradle.kts"
    "$REPO_ROOT/desktopApp/build.gradle.kts"
)

newest_jar() {
    [ -d "$JAR_DIR" ] || return 0
    ls -t "$JAR_DIR"/*.jar 2>/dev/null | head -n 1 || true
}

package_jar() {
    echo "run.sh: $1, packaging the desktop jar..."
    (cd "$REPO_ROOT" && ./gradlew :desktopApp:packageUberJarForCurrentOS --no-daemon)
}

JAR="$(newest_jar)"

if [ -z "$JAR" ]; then
    package_jar "no packaged jar yet"
    JAR="$(newest_jar)"
elif [ -n "$(find "${WATCHED[@]}" -newer "$JAR" -print -quit 2>/dev/null || true)" ]; then
    package_jar "sources are newer than $(basename "$JAR")"
    JAR="$(newest_jar)"
fi

if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
    echo "run.sh: packaging produced no jar under $JAR_DIR" >&2
    exit 1
fi

# The jar is JDK 25 bytecode, so an older runtime on PATH will not do.
REQUIRED_MAJOR=25

java_major() {
    "$1" -version 2>&1 | head -n 1 | sed -E 's/.*version "([0-9]+(\.[0-9]+)?).*/\1/' \
        | awk -F. '{print ($1 == 1 ? $2 : $1)}'
}

# Candidates in priority order: TOKIFACTOR_JAVA, JAVA_HOME, PATH, Gradle's
# provisioned JDKs.
java_candidates() {
    if [ -n "${TOKIFACTOR_JAVA:-}" ]; then
        echo "$TOKIFACTOR_JAVA"
    fi
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        echo "$JAVA_HOME/bin/java"
    fi
    if command -v java >/dev/null 2>&1; then
        command -v java
    fi
    local dir
    for dir in "$HOME"/.gradle/jdks/*; do
        if [ -x "$dir/bin/java" ]; then
            echo "$dir/bin/java"
        elif [ -x "$dir/Contents/Home/bin/java" ]; then
            echo "$dir/Contents/Home/bin/java"
        fi
    done
}

JAVA_BIN=""
FALLBACK_BIN=""
while IFS= read -r candidate; do
    [ -x "$candidate" ] || continue
    [ -z "$FALLBACK_BIN" ] && FALLBACK_BIN="$candidate"
    major="$(java_major "$candidate" || true)"
    if [ -n "$major" ] && [ "$major" -ge "$REQUIRED_MAJOR" ] 2>/dev/null; then
        JAVA_BIN="$candidate"
        break
    fi
done < <(java_candidates)

if [ -z "$JAVA_BIN" ]; then
    if [ -n "$FALLBACK_BIN" ]; then
        echo "run.sh: $(basename "$FALLBACK_BIN") is older than JDK $REQUIRED_MAJOR; launching anyway." >&2
        JAVA_BIN="$FALLBACK_BIN"
    else
        echo "run.sh: no java runtime found; set TOKIFACTOR_JAVA or JAVA_HOME to a JDK $REQUIRED_MAJOR+ install." >&2
        exit 1
    fi
fi

echo "run.sh: launching $(basename "$JAR") with $JAVA_BIN"
cd "$REPO_ROOT"
exec "$JAVA_BIN" -jar "$JAR"
