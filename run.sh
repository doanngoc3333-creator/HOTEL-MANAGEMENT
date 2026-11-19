#!/usr/bin/env bash
# Chạy ứng dụng Hotel Management (Linux / macOS)
#
#   ./run.sh          -> biên dịch (nếu cần) rồi mở giao diện
#   ./run.sh test     -> chỉ kiểm tra kết nối MySQL
#   ./run.sh clean    -> xoá thư mục out/ rồi build lại từ đầu
#
# Có thể truyền thông tin đăng nhập MySQL qua biến môi trường:
#   DB_USER=root DB_PASSWORD=matkhau ./run.sh
# hoặc qua tham số JVM:
#   JAVA_OPTS="-Ddb.password=matkhau" ./run.sh

set -euo pipefail

cd "$(dirname "$0")"

SRC_DIR="src"
OUT_DIR="out"
MAIN_CLASS="hotel.model.App"
TEST_CLASS="hotel.model.DatabaseConnection"

# --- Tìm file JDBC driver -----------------------------------------------
JAR=""
for candidate in "$SRC_DIR"/mysql-connector-*.jar ./*.jar lib/*.jar libs/*.jar; do
    if [ -f "$candidate" ]; then
        JAR="$candidate"
        break
    fi
done

if [ -z "$JAR" ]; then
    echo "LỖI: không tìm thấy file mysql-connector-*.jar trong $SRC_DIR/"
    echo "     tải tại: https://dev.mysql.com/downloads/connector/j/"
    exit 1
fi

# --- Xác định dấu phân cách classpath -----------------------------------
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) SEP=";" ;;   # Git Bash trên Windows
    *)                    SEP=":" ;;
esac
CP="$OUT_DIR${SEP}${JAR}"

# --- Xử lý tham số -------------------------------------------------------
if [ "${1:-}" = "clean" ]; then
    rm -rf "$OUT_DIR"
    shift
fi

# --- Biên dịch -----------------------------------------------------------
NEED_BUILD=0
if [ ! -d "$OUT_DIR" ]; then
    NEED_BUILD=1
elif [ -n "$(find "$SRC_DIR" -name '*.java' -newer "$OUT_DIR" -print -quit 2>/dev/null)" ]; then
    NEED_BUILD=1
fi

if [ "$NEED_BUILD" = "1" ]; then
    echo "Đang biên dịch..."
    mkdir -p "$OUT_DIR"
    mapfile -t SOURCES < <(find "$SRC_DIR" -name '*.java')
    javac -encoding UTF-8 -cp "$JAR" -d "$OUT_DIR" "${SOURCES[@]}"
    echo "Biên dịch xong."
fi

# --- Chạy -----------------------------------------------------------------
if [ "${1:-}" = "test" ]; then
    exec java ${JAVA_OPTS:-} -cp "$CP" "$TEST_CLASS"
else
    exec java ${JAVA_OPTS:-} -cp "$CP" "$MAIN_CLASS"
fi
