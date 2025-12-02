import io.github.cdimascio.dotenv.Dotenv;
import java.sql.*;

public class InsertData {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String dbUrl = dotenv.get("DB_CONNECTION_STRING");

        if (dbUrl == null || dbUrl.isEmpty()) {
            System.out.println("⚠️ Lỗi: Không tìm thấy DB_CONNECTION_STRING trong file .env");
            return;
        }

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            System.out.println(">> Đã kết nối SQL Server.");
            conn.setAutoCommit(false);

            // Insert Orders (Month 3: 41-60)
            String insertOrders = """
                INSERT INTO [order_mgmt].[order] ([order_id], [order_date], [order_status], [actual_delivery_date]) VALUES
                (41, '2024-12-01', 'Completed', '2024-12-03'), (42, '2024-12-02', 'Completed', '2024-12-04'),
                (43, '2024-12-03', 'Completed', '2024-12-05'), (44, '2024-12-04', 'Completed', '2024-12-06'),
                (45, '2024-12-05', 'Completed', '2024-12-07'), (46, '2024-12-06', 'Completed', '2024-12-08'),
                (47, '2024-12-07', 'Completed', '2024-12-09'), (48, '2024-12-08', 'Completed', '2024-12-10'),
                (49, '2024-12-09', 'Completed', '2024-12-11'), (50, '2024-12-10', 'Completed', '2024-12-12'),
                (51, '2024-12-11', 'Completed', '2024-12-13'), (52, '2024-12-12', 'Completed', '2024-12-14'),
                (53, '2024-12-13', 'Completed', '2024-12-15'), (54, '2024-12-14', 'Completed', '2024-12-16'),
                (55, '2024-12-15', 'Completed', '2024-12-17'), (56, '2024-12-16', 'Completed', '2024-12-18'),
                (57, '2024-12-17', 'Completed', '2024-12-19'), (58, '2024-12-18', 'Completed', '2024-12-20'),
                (59, '2024-12-19', 'Completed', '2024-12-21'), (60, '2024-12-20', 'Completed', '2024-12-22')
                """;
            
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(insertOrders);
                System.out.println(">> Đã insert 20 orders (41-60).");
            }

            // Insert Single Items
            String insertSingleItems = """
                INSERT INTO [order_mgmt].[order_single_item] ([order_id], [product_variant_id], [quantity], [unit_price], [unit_profit]) VALUES
                (41, 104, 1, 2000000, 200000), (41, 105, 1, 5000000, 500000),
                (43, 104, 1, 2000000, 200000), (43, 105, 1, 5000000, 500000),
                (45, 104, 1, 2000000, 200000), (45, 105, 1, 5000000, 500000),
                (47, 104, 1, 2000000, 200000), (47, 105, 1, 5000000, 500000),
                (50, 101, 100, 10000, 2000)
                """;
            
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(insertSingleItems);
                System.out.println(">> Đã insert single items.");
            }

            // Insert Combo Items
            String insertComboItems = """
                INSERT INTO [order_mgmt].[order_combo_item] ([order_id], [combo_id], [quantity], [combo_price], [unit_profit]) VALUES
                (41, 3, 1, 1000000, 300000),
                (43, 3, 1, 1000000, 300000),
                (45, 3, 1, 1000000, 300000),
                (47, 3, 1, 1000000, 300000),
                (55, 1, 1, 200000, 50000)
                """;
            
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(insertComboItems);
                System.out.println(">> Đã insert combo items.");
            }

            conn.commit();
            System.out.println(">> ✅ Hoàn tất insert data_month_3!");

        } catch (SQLException e) {
            System.out.println("⚠️ Lỗi SQL: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
