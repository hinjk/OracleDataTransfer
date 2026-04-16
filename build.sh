#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  Oracle Data Transfer Tool — 빌드 & 실행 스크립트 (Linux / macOS)
# ─────────────────────────────────────────────────────────────────────────────

set -e

MAIN_CLASS="OracleDataTransfer"
SRC_FILE="${MAIN_CLASS}.java"
LIB_DIR="lib"
OJDBC_JAR=""

# ojdbc jar 자동 탐색
for jar in "${LIB_DIR}"/ojdbc*.jar; do
    if [ -f "$jar" ]; then
        OJDBC_JAR="$jar"
        break
    fi
done

if [ -z "$OJDBC_JAR" ]; then
    echo "❌  ojdbc jar 파일을 찾을 수 없습니다."
    echo "    ${LIB_DIR}/ 디렉토리에 ojdbc8.jar 또는 ojdbc11.jar 를 배치하세요."
    echo "    다운로드: https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html"
    exit 1
fi

echo "✔  JDBC: ${OJDBC_JAR}"
echo "⚙  컴파일 중..."
javac -cp ".:${OJDBC_JAR}" "${SRC_FILE}"
echo "✔  컴파일 완료"
echo "🚀 실행..."
java -cp ".:${OJDBC_JAR}" "${MAIN_CLASS}"
