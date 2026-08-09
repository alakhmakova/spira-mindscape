@echo off
setlocal

set MAVEN_VERSION=3.9.9
set WRAPPER_DIR=%~dp0.mvn\wrapper
set MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%
set MAVEN_ZIP=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%-bin.zip

rem A `goto` rather than an `if not exist (...)` block on purpose: to skip a block, cmd has
rem to paren-match its way to the closing `)`, and the PowerShell one-liner below is full of
rem parentheses. That mis-parse chopped the `call` line further down ("'md" -q test' is not
rem recognized") on every run where Maven was already unpacked.
if exist "%MAVEN_HOME%\bin\mvn.cmd" goto run

if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
rem Mirrors, in order: Maven Central, then the permanent Apache archive. Central is fronted
rem by a CDN that answers 403 to rate-limited IPs, so one host is not enough (see
rem backend/mvnw for the same list and the full reasoning). The URLs are built by string
rem concatenation so this line needs no escaped double quotes - cmd would treat each one as
rem toggling its own quote state.
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $v='%MAVEN_VERSION%'; $urls=@(('https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/'+$v+'/apache-maven-'+$v+'-bin.zip'),('https://archive.apache.org/dist/maven/maven-3/'+$v+'/binaries/apache-maven-'+$v+'-bin.zip')); $ok=$false; foreach ($attempt in 1..3) { foreach ($u in $urls) { try { Invoke-WebRequest -Uri $u -OutFile '%MAVEN_ZIP%' -UseBasicParsing; $ok=$true; break } catch { Write-Host ('Download failed: ' + $u) } }; if ($ok) { break }; Start-Sleep -Seconds 5 }; if (-not $ok) { Write-Host ('Could not download Apache Maven ' + $v + ' from any mirror.'); exit 1 }; Expand-Archive -Path '%MAVEN_ZIP%' -DestinationPath '%WRAPPER_DIR%' -Force"
if errorlevel 1 exit /b 1

:run
call "%MAVEN_HOME%\bin\mvn.cmd" %*
if errorlevel 1 exit /b %errorlevel%
endlocal
