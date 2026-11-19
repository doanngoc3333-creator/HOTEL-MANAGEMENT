@echo off
REM Chay ung dung Hotel Management (Windows)
REM
REM   run.bat         -> bien dich (neu can) roi mo giao dien
REM   run.bat test    -> chi kiem tra ket noi MySQL
REM   run.bat clean   -> xoa thu muc out\ roi build lai tu dau
REM
REM Truyen mat khau MySQL:
REM   set DB_PASSWORD=matkhau
REM   run.bat

setlocal enabledelayedexpansion
cd /d "%~dp0"

set "SRC_DIR=src"
set "OUT_DIR=out"
set "MAIN_CLASS=hotel.model.App"
set "TEST_CLASS=hotel.model.DatabaseConnection"

REM --- Tim file JDBC driver ---------------------------------------------
set "JAR="
for %%f in ("%SRC_DIR%\mysql-connector-*.jar") do (
    if exist "%%~ff" set "JAR=%%~ff"
)
if not defined JAR (
    for %%f in ("lib\mysql-connector-*.jar") do (
        if exist "%%~ff" set "JAR=%%~ff"
    )
)
if not defined JAR (
    echo LOI: khong tim thay file mysql-connector-*.jar trong %SRC_DIR%\
    echo      tai tai: https://dev.mysql.com/downloads/connector/j/
    pause
    exit /b 1
)

set "CP=%OUT_DIR%;%JAR%"

REM --- Xu ly tham so ------------------------------------------------------
if /i "%~1"=="clean" (
    if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
    shift
)

REM --- Bien dich ---------------------------------------------------------
if not exist "%OUT_DIR%" goto build

set "NEED_BUILD=0"
for /r "%SRC_DIR%" %%f in (*.java) do (
    if %%~tf GTR %OUT_DIR% set "NEED_BUILD=1"
)

:build
if not exist "%OUT_DIR%" (
    echo Dang bien dich...
    mkdir "%OUT_DIR%"
    dir /b /s "%SRC_DIR%\*.java" > "%TEMP%\hm_sources.txt"
    javac -encoding UTF-8 -cp "%JAR%" -d "%OUT_DIR%" "@%TEMP%\hm_sources.txt"
    del "%TEMP%\hm_sources.txt"
    echo Bien dich xong.
)

REM --- Chay ---------------------------------------------------------------
if /i "%~1"=="test" (
    java %JAVA_OPTS% -cp "%CP%" %TEST_CLASS%
) else (
    java %JAVA_OPTS% -cp "%CP%" %MAIN_CLASS%
)

if errorlevel 1 pause
endlocal
