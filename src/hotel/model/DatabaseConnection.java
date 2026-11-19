package hotel.model;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
 */
public final class DatabaseConnection {

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

        // Nạp driver một lần. JDK hiện đại tự dò driver qua ServiceLoader,
        // nhưng gọi Class.forName() giúp báo lỗi rõ ràng khi thiếu file .jar.
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Không tìm thấy MySQL JDBC Driver (com.mysql.cj.jdbc.Driver).\n"
                            + "Hãy thêm src/mysql-connector-j-26.10.0.jar vào classpath / thư viện của project.\n"
                            + "Xem hướng dẫn: docs/HUONG_DAN_KET_NOI_MYSQL.md", e);
        }
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
     *   java -cp "out;src/mysql-connector-j-26.10.0.jar" hotel.model.DatabaseConnection
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
