#!/bin/sh

APP_HOME=$(pwd)
GRADLE_WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

mkdir -p gradle/wrapper

if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
  echo "Downloading Gradle wrapper..."
  pkg install curl -y
  curl -L -o "$GRADLE_WRAPPER_JAR" https://services.gradle.org/distributions/gradle-8.5-bin.zip
fi

java -jar "\( GRADLE_WRAPPER_JAR" " \)@"
