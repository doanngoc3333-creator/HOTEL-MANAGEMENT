package hotel.model;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Giao diện quản lý đặt phòng, đọc/ghi trực tiếp MySQL qua JDBC.
 *
 * Schema thật (Database/hotel_management_full.sql) khác với thiết kế ban đầu
 * của lớp này, nên các câu SQL dưới đây đã được viết lại cho khớp:
 *   - Reservation(Reservation_ID, StartDate, EndDate, BookingStatus, NoOfGuests,
 *                 Guest_ID, TypeID, Hotel_ID)  -> không có cột room_id
 *   - Room(Hotel_ID, Room_Number, ...)         -> khóa chính ghép, không có room_id
 *   - Guests(Guest_ID, Doc_ID, Name) + Guests_Phone(Phone, Guest_ID)
 *   - Số điện thoại và phòng thực tế (Stays) lấy qua truy vấn con
 */
public class HotelManagementUI {

    /** Giá trị ENUM của Reservation.BookingStatus trong MySQL. */
    private static final String[] BOOKING_STATUSES = {
            "Pending", "Confirmed", "CheckedIn", "CheckedOut", "Cancelled", "NoShow"
    };

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Reservation ID", "Guest Name", "Phone", "Room Type",
                    "Room", "Check-in", "Check-out", "Status", "Guests", "Hotel"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable reservationTable = new JTable(tableModel);
    private final JLabel connectionLabel = new JLabel();

    public HotelManagementUI() {
        createWindow();
        testConnection();
        refreshTable();
    }

    private void createWindow() {
        JFrame frame = new JFrame("Hotel Management");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1080, 600));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        root.setBackground(new Color(245, 247, 250));

        JLabel title = new JLabel("RESERVATION MANAGEMENT");
        title.setFont(new Font("Sans Serif", Font.BOLD, 22));
        title.setForeground(new Color(29, 48, 71));

        connectionLabel.setForeground(new Color(101, 114, 130));
        connectionLabel.setText("Connecting to MySQL: " + DatabaseConnection.describe());

        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 4));
        heading.setOpaque(false);
        heading.add(title);
        heading.add(connectionLabel);
        root.add(heading, BorderLayout.NORTH);

        reservationTable.setRowHeight(30);
        reservationTable.setAutoCreateRowSorter(true);
        reservationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        reservationTable.getTableHeader().setFont(new Font("Sans Serif", Font.BOLD, 12));
        reservationTable.setFillsViewportHeight(true);

        // Cột Check-out (index 6) tô đỏ khi đã tới/quá hạn mà đơn còn hiệu lực
        reservationTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                if (!isSelected) {
                    c.setForeground(Color.BLACK);
                }
                c.setFont(table.getFont());

                if (column == 6 && value != null) {
                    try {
                        LocalDate checkOutDate = LocalDate.parse(value.toString());
                        int modelRow = table.convertRowIndexToModel(row);
                        String status = String.valueOf(table.getModel().getValueAt(modelRow, 7));

                        boolean active = !"Cancelled".equals(status)
                                && !"CheckedOut".equals(status)
                                && !"NoShow".equals(status);

                        if (active && !LocalDate.now().isBefore(checkOutDate)) {
                            if (!isSelected) {
                                c.setForeground(new Color(210, 40, 40));
                            }
                            c.setFont(new Font("Sans Serif", Font.BOLD, 12));
                        }
                    } catch (Exception ignored) {
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(reservationTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(222, 227, 233)));
        root.add(scrollPane, BorderLayout.CENTER);

        JButton addButton = createButton("+ Add Reservation", new Color(34, 112, 90));
        JButton editButton = createButton("Edit", new Color(54, 91, 135));
        JButton deleteButton = createButton("Delete", new Color(174, 66, 66));
        JButton refreshButton = createButton("Refresh", new Color(90, 100, 115));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(refreshButton);
        actions.add(addButton);
        actions.add(editButton);
        actions.add(deleteButton);
        root.add(actions, BorderLayout.SOUTH);

        addButton.addActionListener(event -> showReservationDialog(true));
        editButton.addActionListener(event -> editSelectedReservation());
        deleteButton.addActionListener(event -> deleteSelectedReservation());
        refreshButton.addActionListener(event -> {
            testConnection();
            refreshTable();
        });

        reservationTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    editSelectedReservation();
                }
            }
        });

        frame.setContentPane(root);
        frame.setVisible(true);
    }

    private JButton createButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        return button;
    }

    // ------------------------------------------------------------------
    // Kiểm tra kết nối
    // ------------------------------------------------------------------

    private void testConnection() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            connectionLabel.setForeground(new Color(34, 112, 90));
            connectionLabel.setText("Connected: " + conn.getMetaData().getDatabaseProductVersion()
                    + "  |  " + DatabaseConnection.describe());
        } catch (Exception e) {
            connectionLabel.setForeground(new Color(174, 66, 66));
            connectionLabel.setText("MySQL connection failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Add / Edit
    // ------------------------------------------------------------------

    private void showReservationDialog(boolean isNew) {
        int selectedRow = reservationTable.getSelectedRow();
        if (!isNew && selectedRow < 0) {
            showSelectReservationMessage();
            return;
        }

        JTextField guestName = new JTextField(18);
        JTextField docId = new JTextField(18);
        JTextField phone = new JTextField(18);
        JTextField startDate = new JTextField(LocalDate.now().toString(), 18);
        JTextField endDate = new JTextField(LocalDate.now().plusDays(1).toString(), 18);
        JSpinner noOfGuests = new JSpinner(new SpinnerNumberModel(1, 1, 5, 1));

        JComboBox<Item> hotelCombo = new JComboBox<>();
        JComboBox<Item> roomTypeCombo = new JComboBox<>();
        JComboBox<String> status = new JComboBox<>(BOOKING_STATUSES);

        try {
            loadHotels(hotelCombo);
            loadRoomTypes(roomTypeCombo);
        } catch (Exception e) {
            showDatabaseError("Cannot load Hotels / RoomType", e);
            return;
        }

        if (!isNew) {
            guestName.setText(asText(reservationTable.getValueAt(selectedRow, 1)));
            phone.setText(asText(reservationTable.getValueAt(selectedRow, 2)));
            startDate.setText(asText(reservationTable.getValueAt(selectedRow, 5)));
            endDate.setText(asText(reservationTable.getValueAt(selectedRow, 6)));
            status.setSelectedItem(asText(reservationTable.getValueAt(selectedRow, 7)));
            int guests = (int) reservationTable.getValueAt(selectedRow, 8);
            noOfGuests.setValue(Math.min(5, Math.max(1, guests)));
            selectItemByText(hotelCombo, asText(reservationTable.getValueAt(selectedRow, 9)));
            selectItemByText(roomTypeCombo, asText(reservationTable.getValueAt(selectedRow, 3)));

            long reservationId = ((Number) reservationTable.getValueAt(selectedRow, 0)).longValue();
            String existingDocId = fetchGuestDocId(reservationId);
            docId.setText(existingDocId == null ? "" : existingDocId);
        }

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 8));
        form.add(new JLabel("Guest Name:"));
        form.add(guestName);
        form.add(new JLabel("Document ID (CCCD/Passport):"));
        form.add(docId);
        form.add(new JLabel("Phone Number:"));
        form.add(phone);
        form.add(new JLabel("Hotel:"));
        form.add(hotelCombo);
        form.add(new JLabel("Room Type:"));
        form.add(roomTypeCombo);
        form.add(new JLabel("Check-in (yyyy-MM-dd):"));
        form.add(startDate);
        form.add(new JLabel("Check-out (yyyy-MM-dd):"));
        form.add(endDate);
        form.add(new JLabel("Number of guests:"));
        form.add(noOfGuests);
        form.add(new JLabel("Status:"));
        form.add(status);
        form.add(new JLabel(""));
        form.add(new JLabel("<html><i>Room numbers are assigned in Stays (check-in).</i></html>"));

        while (true) {
            int result = JOptionPane.showConfirmDialog(null, form,
                    isNew ? "Add Reservation" : "Edit Reservation",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }

            try {
                String name = guestName.getText().trim();
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Please enter the guest's name.");
                }
                String doc = docId.getText().trim();
                if (doc.isEmpty()) {
                    throw new IllegalArgumentException("Please enter the guest's document ID (CCCD/Passport).");
                }
                String phoneStr = phone.getText().trim();
                if (phoneStr.isEmpty()) {
                    throw new IllegalArgumentException("Please enter the guest's phone number.");
                }

                LocalDate checkIn = LocalDate.parse(startDate.getText().trim());
                LocalDate checkOut = LocalDate.parse(endDate.getText().trim());
                if (checkOut.isBefore(checkIn)) {
                    throw new IllegalArgumentException("Check-out date must not be before the check-in date.");
                }

                Item hotel = (Item) hotelCombo.getSelectedItem();
                Item roomType = (Item) roomTypeCombo.getSelectedItem();
                if (hotel == null || roomType == null) {
                    throw new IllegalArgumentException("Please choose a hotel and a room type.");
                }

                int guests = (Integer) noOfGuests.getValue();
                String selectedStatus = (String) status.getSelectedItem();

                saveReservation(isNew, selectedRow, name, doc, phoneStr,
                        checkIn, checkOut, guests, selectedStatus,
                        hotel.id, roomType.id);

                refreshTable();
                return;

            } catch (IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(null, exception.getMessage(),
                        "Invalid Information", JOptionPane.WARNING_MESSAGE);
            } catch (SQLException exception) {
                // Trigger trong MySQL (sức chứa phòng, ngày...) sẽ ném lỗi ở đây
                JOptionPane.showMessageDialog(null,
                        "MySQL rejected the change:\n" + exception.getMessage(),
                        "Database Error", JOptionPane.WARNING_MESSAGE);
            } catch (Exception exception) {
                exception.printStackTrace();
                JOptionPane.showMessageDialog(null, "Unexpected error: " + exception.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Lưu guest (nếu chưa có) + reservation trong MỘT transaction.
     * Reservation_ID và Guest_ID không AUTO_INCREMENT trong schema nên phải tự sinh.
     */
    private void saveReservation(boolean isNew, int selectedRow,
                                 String name, String doc, String phoneStr,
                                 LocalDate checkIn, LocalDate checkOut,
                                 int noOfGuests, String status,
                                 long hotelId, long roomTypeId) throws SQLException {

        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                long guestId = findOrCreateGuest(conn, name, doc, phoneStr);

                if (isNew) {
                    long reservationId = nextId(conn, "Reservation", "Reservation_ID");
                    String sql = "INSERT INTO Reservation "
                            + "(Reservation_ID, StartDate, EndDate, BookingStatus, NoOfGuests, Guest_ID, TypeID, Hotel_ID) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setLong(1, reservationId);
                        pstmt.setDate(2, java.sql.Date.valueOf(checkIn));
                        pstmt.setDate(3, java.sql.Date.valueOf(checkOut));
                        pstmt.setString(4, status);
                        pstmt.setInt(5, noOfGuests);
                        pstmt.setLong(6, guestId);
                        pstmt.setLong(7, roomTypeId);
                        pstmt.setLong(8, hotelId);
                        pstmt.executeUpdate();
                    }
                } else {
                    long reservationId = ((Number) reservationTable.getValueAt(selectedRow, 0)).longValue();
                    String sql = "UPDATE Reservation SET StartDate = ?, EndDate = ?, BookingStatus = ?, "
                            + "NoOfGuests = ?, Guest_ID = ?, TypeID = ?, Hotel_ID = ? "
                            + "WHERE Reservation_ID = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setDate(1, java.sql.Date.valueOf(checkIn));
                        pstmt.setDate(2, java.sql.Date.valueOf(checkOut));
                        pstmt.setString(3, status);
                        pstmt.setInt(4, noOfGuests);
                        pstmt.setLong(5, guestId);
                        pstmt.setLong(6, roomTypeId);
                        pstmt.setLong(7, hotelId);
                        pstmt.setLong(8, reservationId);
                        pstmt.executeUpdate();
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        }
    }

    /**
     * Tìm guest theo số điện thoại + tên; nếu chưa có thì tạo mới
     * (kèm bản ghi trong Guests_Phone).
     */
    private long findOrCreateGuest(Connection conn, String name, String doc, String phone) throws SQLException {
        String findSql = "SELECT g.Guest_ID FROM Guests g "
                + "JOIN Guests_Phone p ON p.Guest_ID = g.Guest_ID "
                + "WHERE g.Name = ? AND p.Phone = ? LIMIT 1";
        try (PreparedStatement pstmt = conn.prepareStatement(findSql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, phone);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    long guestId = rs.getLong("Guest_ID");
                    ensurePhone(conn, guestId, phone);
                    return guestId;
                }
            }
        }

        long guestId = nextId(conn, "Guests", "Guest_ID");
        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT INTO Guests (Guest_ID, Doc_ID, Name) VALUES (?, ?, ?)")) {
            pstmt.setLong(1, guestId);
            pstmt.setString(2, doc);
            pstmt.setString(3, name);
            pstmt.executeUpdate();
        }
        ensurePhone(conn, guestId, phone);
        return guestId;
    }

    private void ensurePhone(Connection conn, long guestId, String phone) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT IGNORE INTO Guests_Phone (Phone, Guest_ID) VALUES (?, ?)")) {
            pstmt.setString(1, phone);
            pstmt.setLong(2, guestId);
            pstmt.executeUpdate();
        }
    }

    /** Sinh khóa chính kế tiếp vì các bảng không dùng AUTO_INCREMENT. */
    private long nextId(Connection conn, String table, String column) throws SQLException {
        String sql = "SELECT COALESCE(MAX(" + column + "), 0) + 1 AS next_id FROM " + table;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getLong("next_id");
        }
    }

    private String fetchGuestDocId(long reservationId) {
        String sql = "SELECT g.Doc_ID FROM Reservation r "
                + "JOIN Guests g ON g.Guest_ID = r.Guest_ID WHERE r.Reservation_ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, reservationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getString("Doc_ID") : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Delete / Refresh
    // ------------------------------------------------------------------

    private void editSelectedReservation() {
        if (reservationTable.getSelectedRow() < 0) {
            showSelectReservationMessage();
            return;
        }
        showReservationDialog(false);
    }

    private void deleteSelectedReservation() {
        int selectedRow = reservationTable.getSelectedRow();
        if (selectedRow < 0) {
            showSelectReservationMessage();
            return;
        }

        int result = JOptionPane.showConfirmDialog(null, "Delete the selected reservation from database?",
                "Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        long reservationId = ((Number) reservationTable.getValueAt(selectedRow, 0)).longValue();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM Reservation WHERE Reservation_ID = ?")) {
            pstmt.setLong(1, reservationId);
            pstmt.executeUpdate();
            refreshTable();
        } catch (SQLException e) {
            // FK RESTRICT: reservation đã có Stays
            JOptionPane.showMessageDialog(null,
                    "Cannot delete this reservation: it already has a stay/invoice attached.\n"
                            + e.getMessage(),
                    "Database Error", JOptionPane.WARNING_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            showDatabaseError("Cannot delete reservation", e);
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);

        String sql = "SELECT r.Reservation_ID, "
                + "       g.Name AS GuestName, "
                + "       g.Doc_ID AS DocID, "
                + "       (SELECT GROUP_CONCAT(p.Phone ORDER BY p.Phone SEPARATOR ', ') "
                + "          FROM Guests_Phone p WHERE p.Guest_ID = g.Guest_ID) AS Phones, "
                + "       rt.Name AS RoomTypeName, "
                + "       (SELECT s.Room_Number FROM Stays s "
                + "         WHERE s.Reservation_ID = r.Reservation_ID LIMIT 1) AS RoomNumber, "
                + "       r.StartDate, r.EndDate, r.BookingStatus, r.NoOfGuests, "
                + "       h.Name AS HotelName "
                + "FROM Reservation r "
                + "LEFT JOIN Guests g   ON g.Guest_ID = r.Guest_ID "
                + "LEFT JOIN RoomType rt ON rt.TypeID  = r.TypeID "
                + "LEFT JOIN Hotels h    ON h.Hotel_ID = r.Hotel_ID "
                + "ORDER BY r.Reservation_ID";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                java.sql.Date start = rs.getDate("StartDate");
                java.sql.Date end = rs.getDate("EndDate");
                tableModel.addRow(new Object[]{
                        rs.getLong("Reservation_ID"),
                        rs.getString("GuestName"),
                        rs.getString("Phones") == null ? "" : rs.getString("Phones"),
                        rs.getString("RoomTypeName"),
                        rs.getString("RoomNumber") == null ? "Unassigned" : rs.getString("RoomNumber"),
                        start == null ? null : start.toLocalDate(),
                        end == null ? null : end.toLocalDate(),
                        rs.getString("BookingStatus"),
                        rs.getInt("NoOfGuests"),
                        rs.getString("HotelName")
                });
            }
            testConnection();

        } catch (Exception e) {
            e.printStackTrace();
            connectionLabel.setForeground(new Color(174, 66, 66));
            connectionLabel.setText("Cannot load data: " + e.getMessage());
            showDatabaseError("Cannot load reservations from MySQL", e);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void loadHotels(JComboBox<Item> combo) throws SQLException {
        combo.removeAllItems();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT Hotel_ID, Name FROM Hotels ORDER BY Name")) {
            while (rs.next()) {
                combo.addItem(new Item(rs.getLong("Hotel_ID"), rs.getString("Name")));
            }
        }
    }

    private void loadRoomTypes(JComboBox<Item> combo) throws SQLException {
        combo.removeAllItems();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT TypeID, Name, Capacity FROM RoomType ORDER BY Name")) {
            while (rs.next()) {
                combo.addItem(new Item(rs.getLong("TypeID"),
                        rs.getString("Name") + "  (max " + rs.getInt("Capacity") + " pax)"));
            }
        }
    }

    private void selectItemByText(JComboBox<Item> combo, String text) {
        if (text == null) {
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            String label = combo.getItemAt(i).toString();
            if (label.equals(text) || label.startsWith(text + " ")) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static String asText(Object value) {
        return value == null ? "" : value.toString();
    }

    private void showSelectReservationMessage() {
        JOptionPane.showMessageDialog(null, "Please select a reservation in the table.",
                "No Reservation Selected", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showDatabaseError(String title, Exception e) {
        JOptionPane.showMessageDialog(null, title + ":\n" + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
    }

    /** Một mục trong combo box: giữ cả ID (khóa ngoại) và nhãn hiển thị. */
    private static final class Item {
        private final long id;
        private final String label;

        private Item(long id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(HotelManagementUI::new);
    }
}
