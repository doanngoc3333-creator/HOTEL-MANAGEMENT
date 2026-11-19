package hotel.model;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Cầu nối duy nhất giữa ứng dụng Java và MySQL (qua JDBC).
 *
 * Thông tin kết nối có thể ghi đè mà không cần sửa code:
 *   - System property:  -Ddb.url=... -Ddb.user=... -Ddb.password=...
 *   - Biến môi trường:  DB_URL / DB_USER / DB_PASSWORD
 *
 * Mặc định (khớp với file Database/hotel_management_full.sql):
 *   url      = jdbc:mysql://localhost:3306/Hotel_Management_Group3?...
 *   user     = root
 *   password = (rỗng)
 *
 * Về JDBC driver: nếu file mysql-connector-j-*.jar chưa nằm trên classpath, lớp này
 * sẽ TỰ TÌM file .jar trong project và nạp nó lúc chạy. Nhờ vậy bấm Run trong IDE
 * hoặc chạy `java -cp out ...` đều hoạt động mà không cần cấu hình thêm.
 */
public final class DatabaseConnection {

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PORT = "3306";
    private static final String DEFAULT_DATABASE = "Hotel_Management_Group3";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;

    static {
        URL = setting("db.url", "DB_URL", buildDefaultUrl());
        USER = setting("db.user", "DB_USER", DEFAULT_USER);
        PASSWORD = setting("db.password", "DB_PASSWORD", DEFAULT_PASSWORD);

        ensureDriverLoaded();
    }

    private DatabaseConnection() {
    }

    /** Mở một kết nối mới. Luôn dùng try-with-resources để tự đóng. */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static String getUrl() {
        return URL;
    }

    public static String getUser() {
        return USER;
    }

    /** Thông tin ngắn gọn dùng để hiển thị trên giao diện. */
    public static String describe() {
        return USER + "@" + URL.replaceAll("\\?.*$", "");
    }

    // ------------------------------------------------------------------
    // Nạp JDBC driver (có tự dò file .jar nếu thiếu trên classpath)
    // ------------------------------------------------------------------

    private static void ensureDriverLoaded() {
        // 1) Thử cách bình thường: driver đã có trên classpath.
        try {
            Class.forName(DRIVER_CLASS);
            return;
        } catch (ClassNotFoundException ignored) {
            // rơi xuống bước 2
        }

        // 2) Tự tìm file .jar trong project rồi nạp bằng URLClassLoader.
        List<File> jars = findDriverJars();
        for (File jar : jars) {
            try {
                URLClassLoader loader = new URLClassLoader(
                        new URL[]{jar.toURI().toURL()},
                        DatabaseConnection.class.getClassLoader());
                Class<?> driverClass = Class.forName(DRIVER_CLASS, true, loader);
                Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
                // DriverManager chỉ dùng driver do chính classloader của nó nạp,
                // nên phải bọc lại và đăng ký thủ công.
                DriverManager.registerDriver(new DriverShim(driver));
                return;
            } catch (Throwable ignored) {
                // thử file .jar tiếp theo
            }
        }

        // 3) Không tìm được -> báo lỗi kèm hướng dẫn cụ thể.
        StringBuilder message = new StringBuilder()
                .append("Không tìm thấy MySQL JDBC Driver (").append(DRIVER_CLASS).append(").\n\n")
                .append("Đã tìm file .jar tại các vị trí sau nhưng không thấy:\n");
        for (File dir : candidateDirectories()) {
            message.append("  - ").append(dir.getAbsolutePath()).append('\n');
        }
        message.append("\nCách sửa:\n")
                .append("  • Đặt file mysql-connector-j-*.jar vào thư mục src/ của project, hoặc\n")
                .append("  • Thêm file .jar làm library trong IDE, hoặc\n")
                .append("  • Chạy bằng script: ./run.sh  (Linux/macOS)  hoặc  run.bat  (Windows)\n")
                .append("\nXem hướng dẫn: docs/HUONG_DAN_KET_NOI_MYSQL.md");
        throw new IllegalStateException(message.toString());
    }

    /** Các thư mục sẽ được quét để tìm mysql-connector-j-*.jar. */
    private static Set<File> candidateDirectories() {
        Set<File> dirs = new LinkedHashSet<>();

        // Thư mục làm việc hiện tại và các thư mục con thường dùng
        File cwd = new File(System.getProperty("user.dir", "."));
        dirs.add(cwd);
        dirs.add(new File(cwd, "src"));
        dirs.add(new File(cwd, "lib"));
        dirs.add(new File(cwd, "libs"));
        dirs.add(new File(cwd, "drivers"));

        // Vị trí file .class đang chạy (ví dụ: .../out/hotel/model/ -> lùi 3 cấp)
        try {
            File codeSource = new File(
                    DatabaseConnection.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI());
            File dir = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
            for (int i = 0; i < 4 && dir != null; i++) {
                dirs.add(dir);
                dirs.add(new File(dir, "src"));
                dirs.add(new File(dir, "lib"));
                dirs.add(new File(dir, "libs"));
                dir = dir.getParentFile();
            }
        } catch (Exception ignored) {
            // bỏ qua, vẫn còn các thư mục ở trên
        }

        return dirs;
    }

    /** Tìm tất cả file .jar có tên chứa "mysql-connector". */
    private static List<File> findDriverJars() {
        List<File> found = new ArrayList<>();
        for (File dir : candidateDirectories()) {
            if (dir == null || !dir.isDirectory()) {
                continue;
            }
            File[] files = dir.listFiles((d, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".jar") && lower.contains("mysql-connector");
            });
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && !found.contains(f)) {
                        found.add(f);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Bọc driver lại để DriverManager chấp nhận driver do classloader khác nạp.
     */
    private static final class DriverShim implements Driver {
        private final Driver delegate;

        DriverShim(Driver delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return delegate.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }

    // ------------------------------------------------------------------
    // Cấu hình
    // ------------------------------------------------------------------

    private static String buildDefaultUrl() {
        return "jdbc:mysql://" + DEFAULT_HOST + ":" + DEFAULT_PORT + "/" + DEFAULT_DATABASE
                + "?useSSL=false"
                + "&allowPublicKeyRetrieval=true"
                + "&characterEncoding=UTF-8"
                + "&serverTimezone=Asia/Ho_Chi_Minh";
    }

    private static String setting(String propertyKey, String envKey, String fallback) {
        String value = System.getProperty(propertyKey);
        if (value == null || value.isBlank()) {
            value = System.getenv(envKey);
        }
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * Chạy riêng để kiểm tra kết nối:
     *   java -cp "out:src/mysql-connector-j-26.10.0.jar" hotel.model.DatabaseConnection
     */
    public static void main(String[] args) {
        System.out.println("Đang kết nối tới: " + describe());
        try (Connection conn = getConnection()) {
            System.out.println("Kết nối thành công!");
            System.out.println("Database : " + conn.getCatalog());
            System.out.println("MySQL    : " + conn.getMetaData().getDatabaseProductVersion());
            System.out.println("Driver   : " + conn.getMetaData().getDriverVersion());
        } catch (SQLException e) {
            System.out.println("Kết nối thất bại: " + e.getMessage());
            System.out.println("Mã lỗi      : " + e.getErrorCode() + " / SQLState " + e.getSQLState());
        }
    }
}
