@echo off
rem Runs Maven for this project. Uses "mvn" from PATH if available,
rem otherwise falls back to the copy cached under %USERPROFILE%\.m2\wrapper.
rem Usage: run.cmd            (launches the app via javafx:run)
rem        run.cmd <goals>    (e.g. run.cmd -DskipTests compile)
setlocal
cd /d "%~dp0"

if not defined JAVA_HOME (
  for /d %%J in ("%ProgramFiles%\Java\jdk-*") do set "JAVA_HOME=%%J"
)

set "MVN="
where mvn >nul 2>nul && set "MVN=mvn"
if not defined MVN (
  for /d %%D in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-*") do (
    for /d %%E in ("%%D\*") do (
      if exist "%%E\bin\mvn.cmd" set "MVN=%%E\bin\mvn.cmd"
      if exist "%%E\apache-maven-3.9.16\bin\mvn.cmd" set "MVN=%%E\apache-maven-3.9.16\bin\mvn.cmd"
    )
  )
)
if not defined MVN (
  echo Maven not found. Download it from https://maven.apache.org/download.cgi and add its bin folder to PATH.
  exit /b 1
)

if "%~1"=="" (
  call "%MVN%" javafx:run
) else (
  call "%MVN%" %*
)
exit /b %errorlevel%
