@echo off
setlocal
rem Maven launcher: downloads Maven 3.9.11 once into %USERPROFILE%\.m2\wrapper, then runs it.
set "MVN_VER=3.9.11"
set "BASE=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MVN_VER%"
set "MVN_HOME=%BASE%\apache-maven-%MVN_VER%"

if not exist "%MVN_HOME%\bin\mvn.cmd" (
  echo Downloading Maven %MVN_VER% ^(first run only^)...
  if not exist "%BASE%" mkdir "%BASE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MVN_VER%/apache-maven-%MVN_VER%-bin.zip' -OutFile '%BASE%\maven.zip'; Expand-Archive -Force '%BASE%\maven.zip' '%BASE%'; Remove-Item '%BASE%\maven.zip'"
  if errorlevel 1 (
    echo ERROR: could not download Maven. Check your internet connection.
    exit /b 1
  )
)
call "%MVN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
