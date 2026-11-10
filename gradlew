#!/bin/sh
# NEXPLAY Gradle launcher. If gradle-wrapper.jar is missing, it is generated with a locally
# installed Gradle (CI does this automatically). Then the standard wrapper is used.
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  if command -v gradle >/dev/null 2>&1; then
    echo "gradle-wrapper.jar missing: generating wrapper with local Gradle..." >&2
    (cd "$APP_HOME" && gradle wrapper --gradle-version 8.10.2) || exit 1
    # 'gradle wrapper' rewrites this file with the official launcher; run it.
    exec "$APP_HOME/gradlew" "$@"
  else
    echo "ERROR: gradle-wrapper.jar missing and 'gradle' not installed. Use GitHub Actions (it creates it)." >&2
    exit 1
  fi
fi
JAVACMD=${JAVA_HOME:+$JAVA_HOME/bin/}java
exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
