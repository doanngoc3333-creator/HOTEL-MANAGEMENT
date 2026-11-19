# Hướng dẫn nối Java với MySQL (JDBC)

Tài liệu này hướng dẫn từng bước để ứng dụng Java (`src/hotel/model`) nói chuyện được với
MySQL và hiển thị dữ liệu lên giao diện Swing. Toàn bộ các bước dưới đây **đã được chạy thử
thật** với MySQL 5.7.29 + MySQL Connector/J 26.10.0: kết nối OK, đọc 76 reservation OK,
thêm/sửa/xoá OK, và trigger chặn đặt phòng quá sức chứa OK.

---

## 0. Cách hoạt động (bức tranh tổng thể)

```
HotelManagementUI (Swing)
        │  gọi DatabaseConnection.getConnection()
        ▼
DatabaseConnection  ──►  DriverManager  ──►  MySQL Connector/J (JDBC driver, file .jar)
        │                                         │
        │                                         ▼  giao thức MySQL (TCP 3306)
        └──────────── PreparedStatement / ResultSet ─────►  MySQL Server
                                                            └── Database: Hotel_Management_Group3
```

Ba thứ bắt buộc phải có, thiếu một là không nối được:

| # | Thành phần | Trong repo này |
|---|-----------|----------------|
| 1 | MySQL Server đang chạy | cài MySQL 8.0+ (hoặc XAMPP/MySQL Installer) |
| 2 | Database + bảng + dữ liệu | `Database/hotel_management_full.sql` |
| 3 | JDBC driver (`.jar`) trên classpath | `src/mysql-connector-j-26.10.0.jar` |

---

## 1. Cài MySQL và nạp dữ liệu

### 1.1. Cài MySQL
- **Windows/macOS:** tải MySQL Installer / MySQL Community Server, nhớ đặt **root password**.
- **Ubuntu/Debian:** `sudo apt install mysql-server`
- **XAMPP:** bật module MySQL là đủ.

Kiểm tra server đang chạy:

```bash
mysql -u root -p -e "SELECT VERSION();"
```

### 1.2. Nạp schema + dữ liệu mẫu

```bash
# Windows (cmd/PowerShell) – dùng đường dẫn tuyệt đối
mysql -u root -p < Database/hotel_management_full.sql

# macOS/Linux
mysql -u root -p < Database/hotel_management_full.sql
```

Hoặc mở file `Database/hotel_management_full.sql` trong MySQL Workbench rồi bấm ⚡ (Execute).

Kiểm tra đã nạp đúng:

```sql
USE Hotel_Management_Group3;
SHOW TABLES;                  -- phải thấy 16 bảng
SELECT COUNT(*) FROM Reservation;   -- 76
SELECT COUNT(*) FROM Guests;        -- 20
```

> **Lưu ý quan trọng về schema:** bảng `Position` phải được viết trong dấu backtick
> (`` `Position` ``) vì `POSITION` là **từ khoá dành riêng** của MySQL
> (`POSITION('a' IN 'abc')`). Nếu không quote, MySQL sẽ báo lỗi cú pháp ngay dòng
> `CREATE TABLE Position (`. File SQL trong repo đã được sửa sẵn — nếu bạn dùng bản cũ
> thì cần quote lại.

> **MySQL 8.0+ là bắt buộc** nếu bạn muốn chạy `Query/queries.sql`, vì file này dùng
> `WITH ... AS (...)` (CTE) — MySQL 5.7 không hỗ trợ.

---

## 2. Thêm JDBC driver vào project

File `src/mysql-connector-j-26.10.0.jar` đã có sẵn trong repo. Nó là **thư viện**, không phải
source code — nên phải khai báo là *library*, không phải để trong thư mục `src` rồi quên.

### 2.1. IntelliJ IDEA (khuyến nghị)

Repo đã khai báo sẵn trong `HOTEL_MANAGEMENT.iml`, nên thường **không cần làm gì**.
Nếu IDE vẫn báo đỏ `com.mysql.cj.jdbc.Driver` / `java.sql`:

1. `File → Project Structure…` (Ctrl+Alt+Shift+S)
2. Tab **Libraries** → dấu **+** → **Java** → chọn
   `src/mysql-connector-j-26.10.0.jar` → OK
3. Tab **Modules** → module của bạn → **Dependencies** → tick library vừa thêm → **Apply**
4. `Build → Rebuild Project`

Hoặc nhanh nhất: mở file `DatabaseConnection.java`, đặt con trỏ vào `com.mysql.cj.jdbc.Driver`
rồi bấm **Alt+Enter → Add library to classpath**.

### 2.2. Eclipse
Chuột phải project → **Build Path → Configure Build Path → Libraries → Add JARs…** →
chọn `src/mysql-connector-j-26.10.0.jar` → **Apply and Close**.

### 2.3. VS Code
Thêm vào `.vscode/settings.json`:

```json
{
  "java.project.referencedLibraries": [
    "src/mysql-connector-j-26.10.0.jar"
  ]
}
```

---

## 3. Cấu hình thông tin kết nối

Toàn bộ thông tin nằm trong **một chỗ duy nhất**: `src/hotel/model/DatabaseConnection.java`.

```java
// Mặc định khớp với Database/hotel_management_full.sql
URL      = jdbc:mysql://localhost:3306/Hotel_Management_Group3
USER     = root
PASSWORD = ""          // <-- đổi thành mật khẩu MySQL của bạn
```

### Cách A — sửa trực tiếp (đơn giản nhất)
Mở `DatabaseConnection.java`, sửa `DEFAULT_PASSWORD`. **Nhớ đừng commit mật khẩu thật** nếu
repo là công khai.

### Cách B — truyền lúc chạy, không cần sửa code (khuyến nghị)

Ứng dụng đọc theo thứ tự: **system property → biến môi trường → giá trị mặc định**.

```bash
# Dùng system property
java -Ddb.user=root -Ddb.password=matkhau123 \
     -cp "out:src/mysql-connector-j-26.10.0.jar" hotel.model.App

# Dùng biến môi trường
export DB_USER=root
export DB_PASSWORD=matkhau123
java -cp "out:src/mysql-connector-j-26.10.0.jar" hotel.model.App
```

Trên Windows dùng `set DB_PASSWORD=...` và dấu `;` trong classpath.

Nếu MySQL ở máy/cổng khác:

```bash
java -Ddb.url="jdbc:mysql://192.168.1.10:3306/Hotel_Management_Group3?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh" ...
```

### Vì sao URL có mấy tham số đó?

| Tham số | Tác dụng |
|---------|----------|
| `useSSL=false` | bỏ qua SSL khi chạy local (đỡ lỗi handshake) |
| `allowPublicKeyRetrieval=true` | tránh lỗi `Public Key Retrieval is not allowed` với MySQL 8 |
| `characterEncoding=UTF-8` | để tiếng Việt (Nguyễn Văn An…) không bị `?` |
| `serverTimezone=Asia/Ho_Chi_Minh` | tránh lệch giờ và lỗi `server timezone value ... unrecognized` |

---

## 4. Chạy thử

### 4.1. Test kết nối trước khi mở giao diện (nên làm đầu tiên)

`DatabaseConnection` có sẵn hàm `main` để kiểm tra nhanh:

```bash
java -cp "out:src/mysql-connector-j-26.10.0.jar" hotel.model.DatabaseConnection
```

Kết quả mong đợi:

```
Đang kết nối tới: root@jdbc:mysql://localhost:3306/Hotel_Management_Group3
Kết nối thành công!
Database : Hotel_Management_Group3
MySQL    : 8.0.36
Driver   : mysql-connector-j-26.10.0
```

Nếu bước này fail thì đừng mở giao diện — xem bảng lỗi ở mục 7.

### 4.2. Chạy ứng dụng

**Trong IntelliJ:** chuột phải `src/hotel/model/App.java` → **Run 'App.main()'**.

**Bằng dòng lệnh:**

```bash
# macOS/Linux
javac -encoding UTF-8 -cp src/mysql-connector-j-26.10.0.jar -d out $(find src -name "*.java")
java  -cp "out:src/mysql-connector-j-26.10.0.jar" hotel.model.App

# Windows
javac -encoding UTF-8 -cp src/mysql-connector-j-26.10.0.jar -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
java  -cp "out;src/mysql-connector-j-26.10.0.jar" hotel.model.App
```

Giao diện sẽ hiện bảng reservation. Dòng phụ đề phía trên cho biết trạng thái kết nối:
- **xanh** = đã kết nối (`Connected: 8.0.36 | root@...`)
- **đỏ** = lỗi kết nối, kèm lý do

---

## 5. Code nối DB trông như thế nào

### 5.1. Lớp kết nối duy nhất

```java
public final class DatabaseConnection {
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
```

Điểm quan trọng: **luôn dùng try-with-resources** để đóng connection, statement, ResultSet.

```java
try (Connection conn = DatabaseConnection.getConnection();
     PreparedStatement ps = conn.prepareStatement(SQL)) {
    ps.setLong(1, id);
    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) { /* đọc dữ liệu */ }
    }
}
```

### 5.2. Đọc dữ liệu (SELECT)

```java
String sql = "SELECT Guest_ID, Name FROM Guests WHERE Name LIKE ?";
try (Connection conn = DatabaseConnection.getConnection();
     PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setString(1, "%An%");
    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            System.out.println(rs.getLong("Guest_ID") + " - " + rs.getString("Name"));
        }
    }
}
```

### 5.3. Ghi dữ liệu (INSERT/UPDATE/DELETE) + transaction

Schema này **không dùng `AUTO_INCREMENT`**, nên phải tự sinh khoá chính.
`HotelManagementUI.saveReservation()` làm đúng như sau:

```java
conn.setAutoCommit(false);          // bắt đầu transaction
try {
    long guestId = findOrCreateGuest(conn, name, doc, phone);   // 1. Guests + Guests_Phone
    long resId   = nextId(conn, "Reservation", "Reservation_ID");
    // 2. INSERT INTO Reservation (...) VALUES (...)
    conn.commit();                  // tất cả cùng thành công
} catch (SQLException e) {
    conn.rollback();                // hoặc không có gì thay đổi
    throw e;
}
```

Lý do cần transaction: nếu chèn guest xong mà chèn reservation lỗi (ví dụ trigger chặn vì quá
sức chứa), bạn sẽ có một guest "rác" trong DB. `rollback()` xoá sạch dấu vết đó.

---

## 6. Những gì đã được sửa để code khớp với database thật

Code giao diện ban đầu query vào một schema **không tồn tại** trong file `.sql`
(ví dụ `guests.phone`, `rooms.room_id`, `reservations.status`, `reservation_id` tự tăng).
Đã sửa lại cho khớp với `Database/hotel_management_full.sql`:

| Code cũ (sai) | Database thật | Cách xử lý mới |
|---|---|---|
| `guests.phone` | `Guests` không có cột phone | đọc qua bảng `Guests_Phone` |
| `rooms.room_id` | `Room` khoá ghép `(Hotel_ID, Room_Number)` | phòng thực tế lấy từ `Stays` |
| `reservations.status` | `Reservation.BookingStatus` | dùng ENUM đúng: `Pending, Confirmed, CheckedIn, CheckedOut, Cancelled, NoShow` |
| `reservation_id` tự tăng | không `AUTO_INCREMENT` | tự sinh `MAX(id)+1` trong transaction |
| `room_types` | tên bảng là `RoomType` | đổi tên bảng/cột |
| `reservations.room_id` | `Reservation` không có cột này | bỏ; phòng gán lúc check-in (`Stays`) |

Ngoài ra:
- `DatabaseConnection` báo lỗi rõ ràng khi thiếu driver, và có `main()` để test kết nối.
- `App.java` giờ mở giao diện thay vì chỉ in `Hello, World!`.
- `HOTEL_MANAGEMENT.iml` khai báo sẵn thư viện `mysql-connector-j` cho IntelliJ.

> **Chưa làm:** thêm phòng cho một reservation (bảng `Stays`) và phần hoá đơn/thanh toán
> (`Invoices`, `InvoiceLine`, `Payment`) chưa có trên giao diện. Xem mục 9 nếu muốn mở rộng.

---

## 7. Bảng lỗi thường gặp

| Lỗi | Nguyên nhân | Cách sửa |
|-----|-------------|----------|
| `No suitable driver found for jdbc:mysql://...` | driver chưa có trên classpath | thêm `mysql-connector-j-26.10.0.jar` (mục 2) |
| `Cannot find MySQL JDBC Driver (com.mysql.cj.jdbc.Driver)` | như trên, do `DatabaseConnection` báo | như trên |
| `ClassNotFoundException: com.mysql.cj.jdbc.Driver` | chạy sai classpath, thiếu `.jar` | kiểm tra lệnh `java -cp ...` |
| `Access denied for user 'root'@'localhost'` | sai mật khẩu | sửa `DEFAULT_PASSWORD` hoặc `-Ddb.password=` |
| `Unknown database 'Hotel_Management_Group3'` | chưa nạp file SQL | chạy lại mục 1.2 |
| `Communications link failure` | MySQL chưa chạy / sai cổng / firewall | bật service MySQL, kiểm tra port 3306 |
| `Public Key Retrieval is not allowed` | MySQL 8 + user dùng `caching_sha2_password` | thêm `allowPublicKeyRetrieval=true` (đã có sẵn) |
| `The server time zone value ... is unrecognized` | thiếu `serverTimezone` | đã có `serverTimezone=Asia/Ho_Chi_Minh` |
| Tiếng Việt hiện `?` | sai charset | `characterEncoding=UTF-8` + `javac -encoding UTF-8` |
| `Table 'Hotel_Management_Group3.Position' doesn't exist` | DDL cũ tạo bảng `position` khác tên | nạp lại file SQL đã sửa |
| Lỗi cú pháp tại `CREATE TABLE Position (` | `POSITION` là từ khoá | quote thành `` `Position` `` |
| `Number of guests exceeds the capacity of the room type` | **trigger đang bảo vệ dữ liệu** — không phải bug | chọn `NoOfGuests` ≤ `RoomType.Capacity` |
| `This hotel has no room of the reserved room type` | khách sạn đó không có loại phòng này | chọn cặp Hotel + RoomType hợp lệ |
| `Cannot delete this reservation: it already has a stay` | FK `ON DELETE RESTRICT` | xoá `Stays` liên quan trước, hoặc đổi trạng thái sang `Cancelled` |

---

## 8. Kiểm tra nhanh bằng SQL (không cần Java)

Nếu giao diện báo lỗi, chạy mấy câu này trong Workbench để khoanh vùng vấn đề:

```sql
USE Hotel_Management_Group3;

-- Dữ liệu có đúng không?
SELECT COUNT(*) FROM Reservation;   -- 76
SELECT COUNT(*) FROM Guests;        -- 20

-- Câu query mà giao diện dùng để load bảng:
SELECT r.Reservation_ID, g.Name, rt.Name, h.Name, r.BookingStatus
FROM Reservation r
LEFT JOIN Guests g   ON g.Guest_ID = r.Guest_ID
LEFT JOIN RoomType rt ON rt.TypeID = r.TypeID
LEFT JOIN Hotels h    ON h.Hotel_ID = r.Hotel_ID
LIMIT 5;
```

Nếu câu SQL chạy được trong Workbench mà Java vẫn lỗi → vấn đề ở **classpath / driver /
mật khẩu**, không phải ở SQL.

---

## 9. Muốn mở rộng thêm

| Việc cần làm | Bảng liên quan | Gợi ý |
|---|---|---|
| Gán phòng khi check-in | `Stays` | `INSERT INTO Stays (StayID, CheckIn, Checkout, Reservation_ID, Invoice_ID, Hotel_ID, Room_Number)` — nhớ sinh `Invoice_ID` trước vì là FK bắt buộc |
| Tính hoá đơn | `InvoiceLine`, `Records` | xem view `v_stay_bill_lines` trong `Query/queries.sql` |
| Ghi nhận thanh toán | `Payment` | `Amount > 0` (CHECK constraint) |
| Tìm phòng trống | `Room`, `Reservation`, `Stays` | dùng nguyên câu Q1 trong `Query/queries.sql` |

Khi thêm chức năng, giữ nguyên pattern: **một `DatabaseConnection` duy nhất**, **PreparedStatement**,
**try-with-resources**, và **transaction** cho các thao tác ghi nhiều bảng.

---

## 10. Tóm tắt 4 bước

```bash
# 1. Nạp database
mysql -u root -p < Database/hotel_management_full.sql

# 2. Sửa mật khẩu MySQL trong src/hotel/model/DatabaseConnection.java
#    (hoặc dùng -Ddb.password=...)

# 3. Test kết nối
java -cp "out:src/mysql-connector-j-26.10.0.jar" hotel.model.DatabaseConnection

# 4. Chạy app
java -cp "out:src/mysql-connector-j-26.10.0.jar" hotel.model.App
```

Xong. Nếu bước 3 in `Kết nối thành công!` thì giao diện chắc chắn chạy được.
