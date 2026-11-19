package hotel.model;

import javax.swing.SwingUtilities;

/**
 * Điểm khởi chạy của ứng dụng.
 *
 * Chạy từ dòng lệnh:
 *   javac -encoding UTF-8 -cp src/mysql-connector-j-26.10.0.jar -d out $(find src -name "*.java")
 *   java  -cp "out:src/mysql-connector-j-26.10.0.jar" hotel.model.App
 *
 * (Trên Windows, dùng "out;src/mysql-connector-j-26.10.0.jar" - dấu ; thay vì :)
 */
public class App {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(HotelManagementUI::new);
    }
}
