#!/usr/bin/env sh
# Gradle wrapper script
APP_HOME="$(dirname "$(readlink -f "$0" 2>/dev/null || echo "$0")")"
exec "${JAVA_HOME}/bin/java" \
    -classpath "${APP_HOME}/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
