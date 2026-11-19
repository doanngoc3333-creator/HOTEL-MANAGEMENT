#!/usr/bin/env bash
# Chạy ứng dụng Hotel Management (Linux / macOS)
#
#   ./run.sh          -> biên dịch (nếu cần) rồi mở giao diện
#   ./run.sh test     -> chỉ kiểm tra kết nối MySQL
#   ./run.sh clean    -> xoá thư mục out/ rồi build lại từ đầu
#   ./run.sh doctor   -> chẩn đoán môi trường (Java, MySQL, driver)
#
# Truyền thông tin đăng nhập MySQL qua biến môi trường:
#   DB_USER=root DB_PASSWORD=matkhau ./run.sh
# hoặc qua tham số JVM:
#   JAVA_OPTS="-Ddb.password=matkhau" ./run.sh

set -uo pipefail

cd "$(dirname "$0")"

SRC_DIR="src"
OUT_DIR="out"
MAIN_CLASS="hotel.model.App"
TEST_CLASS="hotel.model.DatabaseConnection"

# ----------------------------------------------------------------------
# Tìm Java (ưu tiên JDK có javac, nhưng vẫn chạy được với JRE/JBR)
# ----------------------------------------------------------------------
find_java_bins() {
    local dir
    for dir in \
        "${JAVA_HOME:-}" \
        /usr/lib/jvm/* \
        /usr/java/* \
        /opt/java/* \
        /opt/jdk* \
        /opt/idea*/jbr \
        /opt/intellij*/jbr \
        /snap/intellij-idea*/current/jbr \
        "$HOME"/.jdks/* \
        "$HOME"/.sdkman/candidates/java/* \
        "$HOME"/.local/share/JetBrains/Toolbox/apps/*/*/jbr \
        "/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home" \
        /Library/Java/JavaVirtualMachines/*/Contents/Home
    do
        [ -n "$dir" ] && [ -x "$dir/bin/java" ] && echo "$dir"
    done
}

# Ưu tiên: bản cài có javac (JDK) -> bản chỉ có java (JRE/JBR của IntelliJ)
JAVA_BIN=""
JAVAC_BIN=""

# Gom mọi bản Java tìm được, bỏ trùng lặp
FOUND_HOMES=()
while IFS= read -r home; do
    [ -z "$home" ] && continue
    case " ${FOUND_HOMES[*]-} " in
        *" $home "*) continue ;;
    esac
    FOUND_HOMES+=("$home")
done < <(find_java_bins)

# Lượt 1: chọn bản có javac
for home in "${FOUND_HOMES[@]-}"; do
    if [ -x "$home/bin/java" ] && [ -x "$home/bin/javac" ]; then
        JAVA_BIN="$home/bin/java"
        JAVAC_BIN="$home/bin/javac"
        break
    fi
done

# Lượt 2: không có JDK -> dùng bản có java (đã biên dịch sẵn thì vẫn chạy được)
if [ -z "$JAVA_BIN" ]; then
    for home in "${FOUND_HOMES[@]-}"; do
        if [ -x "$home/bin/java" ]; then
            JAVA_BIN="$home/bin/java"
            break
        fi
    done
fi

# Cuối cùng: dựa vào PATH
[ -z "$JAVA_BIN" ]  && command -v java  >/dev/null 2>&1 && JAVA_BIN="$(command -v java)"
[ -z "$JAVAC_BIN" ] && command -v javac >/dev/null 2>&1 && JAVAC_BIN="$(command -v javac)"

# --- doctor: in ra tình trạng môi trường ---------------------------------
if [ "${1:-}" = "doctor" ]; then
    echo "==== CHẨN ĐOÁN MÔI TRƯỜNG ===="
    echo "Thư mục     : $(pwd)"
    echo "java        : ${JAVA_BIN:-KHÔNG TÌM THẤY}"
    [ -n "$JAVA_BIN" ] && "$JAVA_BIN" -version 2>&1 | sed 's/^/              /'
    echo "javac       : ${JAVAC_BIN:-KHÔNG TÌM THẤY}  <-- cần để biên dịch"
    [ -n "$JAVAC_BIN" ] && "$JAVAC_BIN" -version 2>&1 | sed 's/^/              /'
    echo "JAVA_HOME   : ${JAVA_HOME:-<chưa đặt>}"
    local_jar=$(ls "$SRC_DIR"/mysql-connector-*.jar 2>/dev/null | head -1)
    echo "JDBC driver : ${local_jar:-KHÔNG TÌM THẤY}"
    echo -n "mysql client: "
    command -v mysql >/dev/null 2>&1 && mysql --version || echo "KHÔNG TÌM THẤY"
    echo -n "MySQL server: "
    if (command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet mysql 2>/dev/null) \
       || (command -v systemctl >/dev/null 2>&1 && systemctl is-active --quiet mariadb 2>/dev/null); then
        echo "đang chạy"
    elif ss -ltn 2>/dev/null | grep -q ':3306'; then
        echo "đang chạy (cổng 3306 mở)"
    else
        echo "KHÔNG CHẠY (hoặc chưa cài)"
    fi
    echo "lớp đã biên dịch: $(find "$OUT_DIR" -name '*.class' 2>/dev/null | wc -l) file"
    exit 0
fi

# ----------------------------------------------------------------------
# Tìm file JDBC driver
# ----------------------------------------------------------------------
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

# Dấu phân cách classpath
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) SEP=";" ;;
    *)                    SEP=":" ;;
esac
CP="$OUT_DIR${SEP}${JAR}"

# ----------------------------------------------------------------------
# Tham số
# ----------------------------------------------------------------------
if [ "${1:-}" = "clean" ]; then
    rm -rf "$OUT_DIR"
    shift
fi

# ----------------------------------------------------------------------
# Biên dịch
# ----------------------------------------------------------------------
CLASS_COUNT=$(find "$OUT_DIR" -name '*.class' 2>/dev/null | wc -l)
NEED_BUILD=0

if [ "$CLASS_COUNT" -eq 0 ]; then
    NEED_BUILD=1
elif [ -n "$(find "$SRC_DIR" -name '*.java' -newer "$OUT_DIR" -print -quit 2>/dev/null)" ]; then
    NEED_BUILD=1
fi

if [ "$NEED_BUILD" = "1" ]; then
    if [ -z "$JAVAC_BIN" ]; then
        if [ "$CLASS_COUNT" -gt 0 ]; then
            echo "Cảnh báo: không tìm thấy 'javac', dùng lớp đã biên dịch sẵn trong $OUT_DIR/"
        else
            cat <<'EOF'
LỖI: không tìm thấy trình biên dịch Java (javac).

Bạn cần cài JDK (không phải chỉ JRE). Trên Ubuntu / Linux Mint / Debian:

    sudo apt update
    sudo apt install openjdk-21-jdk

Sau đó chạy lại:  ./run.sh

Kiểm tra bằng:    javac -version

(Nếu bạn dùng IntelliJ IDEA, có thể bấm Build → Build Project trước,
 rồi chạy ./run.sh — script sẽ dùng các file .class đã biên dịch.)
EOF
            exit 1
        fi
    else
        echo "Đang biên dịch..."
        mkdir -p "$OUT_DIR"
        mapfile -t SOURCES < <(find "$SRC_DIR" -name '*.java')
        if ! "$JAVAC_BIN" -encoding UTF-8 -cp "$JAR" -d "$OUT_DIR" "${SOURCES[@]}"; then
            echo
            echo "Biên dịch THẤT BẠI. Xem lỗi phía trên."
            exit 1
        fi
        echo "Biên dịch xong."
    fi
fi

# ----------------------------------------------------------------------
# Chạy
# ----------------------------------------------------------------------
if [ -z "$JAVA_BIN" ]; then
    echo "LỖI: không tìm thấy 'java' để chạy ứng dụng."
    echo "     Cài JDK:  sudo apt install openjdk-21-jdk"
    exit 1
fi

if [ "${1:-}" = "test" ]; then
    exec "$JAVA_BIN" ${JAVA_OPTS:-} -cp "$CP" "$TEST_CLASS"
else
    exec "$JAVA_BIN" ${JAVA_OPTS:-} -cp "$CP" "$MAIN_CLASS"
fi
