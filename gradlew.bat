@echo off
rem NEXPLAY Gradle launcher for Windows. Generates the wrapper with a local Gradle if the jar is missing.
set APP_HOME=%~dp0
if not exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  where gradle >nul 2>nul
  if errorlevel 1 (
    echo ERROR: gradle-wrapper.jar missing and gradle not installed. Use GitHub Actions.
    exit /b 1
  )
  pushd "%APP_HOME%"
  gradle wrapper --gradle-version 8.10.2
  popd
  call "%APP_HOME%gradlew.bat" %*
  exit /b %errorlevel%
)
java -Dorg.gradle.appname=gradlew -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
