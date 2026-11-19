# Hướng dẫn cài đặt môi trường (từ số 0)

Bạn cần **2 thứ**: **JDK** (để biên dịch/chạy Java) và **MySQL Server** (để chứa dữ liệu).
Nếu mới cài lại máy hoặc máy chưa có gì, làm theo đúng thứ tự dưới đây.

Kiểm tra nhanh mình đang thiếu gì:

```bash
cd HOTEL-MANAGEMENT-arena-27cfbb3c-hotel-management
./run.sh doctor
```

Kết quả sẽ liệt kê: `java`, `javac`, `JDBC driver`, `MySQL server` — cái nào ghi
`KHÔNG TÌM THẤY` thì cài theo mục tương ứng bên dưới.

---

## 1. Cài JDK (bắt buộc)

`javac: command not found` nghĩa là máy chưa có JDK. **JRE không đủ** — phải là JDK.

### Ubuntu / Linux Mint / Debian / Pop!_OS

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk
javac -version      # phải in: javac 21.x.x
java  -version      # phải in: openjdk version "21.x.x"
```

### Fedora

```bash
sudo dnf install -y java-21-openjdk-devel
```

### Arch / Manjaro

```bash
sudo pacman -S --needed jdk21-openjdk
```

### Windows

Tải **JDK 21** tại <https://adoptium.net> → chạy file `.msi` → **nhớ tick**
*“Set JAVA_HOME variable”* và *“Add to PATH”* → mở cmd mới và kiểm tra:

```bat
javac -version
```

### macOS

```bash
brew install --cask temurin@21
```

> **Chỉ dùng IntelliJ mà không muốn cài JDK?** IntelliJ có sẵn JDK (JBR) nhưng **không có
> `javac`**. Khi đó hãy bấm `Build → Build Project` trong IntelliJ trước, rồi chạy
> `./run.sh` — script sẽ tự tìm JBR và dùng các file `.class` đã biên dịch.
> Tuy vậy cài JDK vẫn là cách gọn nhất.

---

## 2. Cài MySQL Server (bắt buộc)

`mysql: command not found` có thể là **chưa cài**, hoặc **đã cài nhưng chưa có trong PATH**
(ví dụ chỉ cài MySQL Workbench). Kiểm tra server có đang chạy không:

```bash
ss -ltn | grep 3306      # có dòng LISTEN ... :3306  => server đang chạy
systemctl status mysql   # hoặc mariadb
```

### Ubuntu / Linux Mint / Debian

```bash
sudo apt update
sudo apt install -y mysql-server
sudo systemctl enable --now mysql
sudo mysql_secure_installation     # đặt mật khẩu root (nên làm)
```

Kiểm tra:

```bash
sudo mysql -e "SELECT VERSION();"
mysql -u root -p -e "SELECT VERSION();"
```

### Fedora

```bash
sudo dnf install -y mysql-server
sudo systemctl enable --now mysqld
sudo mysql_secure_installation
```

### Windows

Tải **MySQL Installer** tại <https://dev.mysql.com/downloads/installer/> →
chọn *MySQL Server 8.0* → trong lúc cài đặt **root password** (nhớ kỹ mật khẩu này) →
tick *“Configure MySQL Server as a Windows Service”*.

Nếu `mysql` không có trong PATH, thêm thư mục này vào PATH (thay số phiên bản cho đúng):

```
C:\Program Files\MySQL\MySQL Server 8.0\bin
```

### macOS

```bash
brew install mysql
brew services start mysql
mysql_secure_installation
```

### XAMPP (Windows/macOS/Linux — dễ nhất cho người mới)

Tải <https://www.apachefriends.org> → mở XAMPP Control Panel → bấm **Start** ở dòng MySQL.
Cổng mặc định 3306. User `root`, mật khẩu **rỗng**.

> **Lưu ý phiên bản:** `Query/queries.sql` dùng `WITH ... AS (...)` (CTE) nên cần
> **MySQL 8.0 trở lên**. MySQL 5.7 cài qua `apt` trên Ubuntu 20.04 sẽ không chạy được
> 4 câu Q1/Q3/Q5/Q11 — phần giao diện Java thì vẫn chạy bình thường trên 5.7.

---

## 3. Nạp database

Sau khi MySQL chạy:

```bash
# có mật khẩu
mysql -u root -p < Database/hotel_management_full.sql

# mật khẩu rỗng (XAMPP)
mysql -u root < Database/hotel_management_full.sql
```

Kiểm tra:

```bash
mysql -u root -p -e "USE Hotel_Management_Group3; SHOW TABLES; SELECT COUNT(*) FROM Reservation;"
```

Kết quả mong đợi: **16 bảng** và **76** reservation.

Nếu bạn dùng GUI: mở MySQL Workbench → `File → Open SQL Script…` → chọn file
`Database/hotel_management_full.sql` → bấm ⚡.

---

## 4. Cấu hình mật khẩu cho ứng dụng

Mở `src/hotel/model/DatabaseConnection.java`, tìm dòng:

```java
private static final String DEFAULT_PASSWORD = "";
```

Điền mật khẩu MySQL của bạn vào giữa hai dấu ngoặc kép.

**Không muốn sửa code?** Truyền qua biến môi trường:

```bash
DB_PASSWORD=matkhau ./run.sh
```

Hoặc system property:

```bash
JAVA_OPTS="-Ddb.password=matkhau" ./run.sh
```

---

## 5. Chạy ứng dụng

```bash
./run.sh test      # 1. kiểm tra kết nối MySQL
./run.sh           # 2. mở giao diện
```

Kết quả `./run.sh test` mong đợi:

```
Đang kết nối tới: root@jdbc:mysql://localhost:3306/Hotel_Management_Group3
Kết nối thành công!
Database : Hotel_Management_Group3
MySQL    : 8.0.36
Driver   : mysql-connector-j-26.10.0
```

---

## 6. Chạy trong IntelliJ IDEA (nếu thích bấm nút Run)

1. `File → Open…` → chọn thư mục project → **Trust Project**
2. Kiểm tra SDK: `File → Project Structure → Project` → *SDK* phải có tên (ví dụ
   `21` hoặc `Temurin-21`). Nếu ghi `<No SDK>` → bấm **Add SDK → Download JDK** → chọn 21.
3. `File → Project Structure → Modules → Dependencies` → phải thấy
   `mysql-connector-j-26.10.0` (file `.iml` trong repo đã khai báo sẵn). Nếu không thấy:
   **+ → JARs or Directories…** → chọn `src/mysql-connector-j-26.10.0.jar` → **Apply**.
4. `Build → Build Project` (Ctrl+F9)
5. Mở `src/hotel/model/App.java` → bấm ▶ (Ctrl+Shift+F10)

Nếu bấm Run báo `ClassNotFoundException: com.mysql.cj.jdbc.Driver`: xem
[docs/HUONG_DAN_KET_NOI_MYSQL.md](HUONG_DAN_KET_NOI_MYSQL.md) mục **2.4**.

---

## 7. Lỗi hay gặp khi cài đặt

| Thông báo | Nghĩa là | Cách sửa |
|---|---|---|
| `javac: command not found` | chưa có JDK (chỉ có JRE) | mục 1 |
| `mysql: command not found` | chưa cài MySQL client hoặc chưa có trong PATH | mục 2 |
| `Can't connect to local MySQL server through socket` | MySQL chưa chạy | `sudo systemctl start mysql` |
| `Access denied for user 'root'@'localhost'` | sai mật khẩu | mục 4 |
| `Unknown database 'Hotel_Management_Group3'` | chưa nạp file SQL | mục 3 |
| `E: Unable to locate package openjdk-21-jdk` | apt quá cũ (Ubuntu 18.04) | `sudo apt install openjdk-17-jdk` rồi sửa `languageLevel` trong `.idea/misc.xml` |
| `cannot open shared object file` khi chạy mysql | thiếu thư viện | `sudo apt install -y libaio1` (Ubuntu ≤ 20.04) hoặc `libaio1t64` (24.04) |
| Cổng 3306 bị chiếm | đã có MySQL/MariaDB khác chạy | `sudo ss -ltnp \| grep 3306` để xem tiến trình |

---

## 8. Tóm tắt một khối lệnh (Ubuntu/Mint)

```bash
# 1. Cài JDK + MySQL
sudo apt update
sudo apt install -y openjdk-21-jdk mysql-server
sudo systemctl enable --now mysql

# 2. Nạp database
cd /media/ngoc/01DD1AFC39B532C0/HOTEL-MANAGEMENT-arena-27cfbb3c-hotel-management
sudo mysql < Database/hotel_management_full.sql

# 3. Chạy
./run.sh doctor    # xem còn thiếu gì
./run.sh test      # kiểm tra kết nối
./run.sh           # mở giao diện
```
