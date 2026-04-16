@echo off
rem ─────────────────────────────────────────────────────────────────────────
rem  Oracle Data Transfer Tool — 빌드 & 실행 스크립트 (Windows)
rem ─────────────────────────────────────────────────────────────────────────

setlocal enabledelayedexpansion

set MAIN_CLASS=OracleDataTransfer
set SRC_FILE=%MAIN_CLASS%.java
set LIB_DIR=lib
set OJDBC_JAR=

rem ojdbc jar 자동 탐색
for %%f in (%LIB_DIR%\ojdbc*.jar) do (
    if exist "%%f" (
        set OJDBC_JAR=%%f
        goto :found
    )
)

echo [ERROR] ojdbc jar 파일을 찾을 수 없습니다.
echo         %LIB_DIR%\ 디렉토리에 ojdbc8.jar 또는 ojdbc11.jar 를 배치하세요.
echo         다운로드: https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html
pause
exit /b 1

:found
echo [OK]  JDBC: %OJDBC_JAR%
echo [..] 컴파일 중...
javac -cp ".;%OJDBC_JAR%" %SRC_FILE%
if errorlevel 1 (
    echo [ERROR] 컴파일 실패
    pause
    exit /b 1
)
echo [OK]  컴파일 완료
echo [>>] 실행...
java -cp ".;%OJDBC_JAR%" %MAIN_CLASS%
