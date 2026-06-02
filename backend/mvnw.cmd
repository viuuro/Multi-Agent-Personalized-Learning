@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------

@echo off
setlocal enabledelayedexpansion

set "MAVEN_PROJECTBASEDIR=%CD%"
if not "%CD%"=="" set MAVEN_PROJECTBASEDIR=%CD%

set "WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain"

set "MVNW_REPOURL=https://repo.maven.apache.org/maven2"
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9"
set "MVNW_VER=3.3.2"

if not defined MAVEN_OPTS set MAVEN_OPTS=-Xmx1024m

if not "%JAVA_HOME%"=="" (
    set "JAVA_EXE=%JAVA_HOME%\bin\javaw.exe"
) else (
    set "JAVA_EXE=javaw"
)

if exist "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9\bin\mvn.cmd" (
    set "MVN_CMD=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9\bin\mvn.cmd"
    goto :run
)

if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"
set "MVN_DIST_ZIP=%MAVEN_HOME%\apache-maven-3.9.9-bin.zip"

if not exist "%MVN_DIST_ZIP%" (
    echo Downloading Apache Maven 3.9.9...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip' -OutFile '%MVN_DIST_ZIP%'}"
    if %ERRORLEVEL% NEQ 0 goto :error
    echo Extracting Maven...
    powershell -Command "& {Expand-Archive -Path '%MVN_DIST_ZIP%' -DestinationPath '%MAVEN_HOME%' -Force}"
    if %ERRORLEVEL% NEQ 0 goto :error
    echo Maven 3.9.9 installed successfully.
)

set "MVN_CMD=%MAVEN_HOME%\apache-maven-3.9.9\bin\mvn.cmd"

:run
"%MVN_CMD%" %*

goto :end

:error
echo ERROR: Failed to set up Maven.
exit /b 1

:end
endlocal
