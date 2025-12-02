import io.github.cdimascio.dotenv.Dotenv;
import java.sql.*;

public class verify_topk {
    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String dbUrl = dotenv.get("DB_CONNECTION_STRING");
        
        int maxTID = 40; // Thay đổi TID tại đây
        
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            System.out.println("=== KIỂM TRA KẾT QUẢ TOP-K (TID <= " + maxTID + ") ===\n");
            
            // 1. {cb3, 105}
            System.out.println("[RANK 1] {cb3, 105}:");
            long sum105_cb3 = queryPattern2(conn, 105, 3, maxTID);
            System.out.println("  >> Tổng = " + sum105_cb3 + " (Expected: 6900000)\n");
            
            // 2. {105}
            System.out.println("[RANK 2] {105}:");
            long sum105 = querySingleItem(conn, 105, maxTID);
            System.out.println("  >> Tổng = " + sum105 + " (Expected: 6000000)\n");
            
            // 3. {cb3}
            System.out.println("[RANK 3] {cb3}:");
            long sumCb3 = queryComboItem(conn, 3, maxTID);
            System.out.println("  >> Tổng = " + sumCb3 + " (Expected: 2400000)\n");
            
            // 4. {104, 105}
            System.out.println("[RANK 4] {104, 105}:");
            long sum104_105 = queryPattern2Singles(conn, 104, 105, maxTID);
            System.out.println("  >> Tổng = " + sum104_105 + " (Expected: 2100000)\n");
            
            // 5. {104}
            System.out.println("[RANK 5] {104}:");
            long sum104 = querySingleItem(conn, 104, maxTID);
            System.out.println("  >> Tổng = " + sum104 + " (Expected: 1400000)\n");
        }
    }
    
    // Query single item (product_variant_id)
    static long querySingleItem(Connection conn, int itemId, int maxTID) throws Exception {
        String sql = """
            SELECT o.order_id, SUM(si.quantity * si.unit_profit) as util
            FROM [order_mgmt].[order_single_item] si
            JOIN [order_mgmt].[order] o ON si.order_id = o.order_id
            WHERE o.order_status = 'Completed' AND o.actual_delivery_date IS NOT NULL
              AND si.product_variant_id = ? AND o.order_id <= ?
            GROUP BY o.order_id
            ORDER BY o.order_id
        """;
        long total = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, maxTID);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long util = rs.getLong("util");
                total += util;
                System.out.println("  Order " + rs.getInt("order_id") + ": " + util);
            }
        }
        return total;
    }
    
    // Query combo item
    static long queryComboItem(Connection conn, int comboId, int maxTID) throws Exception {
        String sql = """
            SELECT o.order_id, SUM(ci.quantity * ci.unit_profit) as util
            FROM [order_mgmt].[order_combo_item] ci
            JOIN [order_mgmt].[order] o ON ci.order_id = o.order_id
            WHERE o.order_status = 'Completed' AND o.actual_delivery_date IS NOT NULL
              AND ci.combo_id = ? AND o.order_id <= ?
            GROUP BY o.order_id
            ORDER BY o.order_id
        """;
        long total = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, comboId);
            ps.setInt(2, maxTID);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long util = rs.getLong("util");
                total += util;
                System.out.println("  Order " + rs.getInt("order_id") + ": " + util);
            }
        }
        return total;
    }
    
    // Query pattern {single_item, combo}
    static long queryPattern2(Connection conn, int singleId, int comboId, int maxTID) throws Exception {
        String sql = """
            SELECT o.order_id
            FROM [order_mgmt].[order] o
            WHERE o.order_status = 'Completed' AND o.actual_delivery_date IS NOT NULL
              AND o.order_id <= ?
              AND EXISTS (SELECT 1 FROM [order_mgmt].[order_single_item] si WHERE si.order_id = o.order_id AND si.product_variant_id = ?)
              AND EXISTS (SELECT 1 FROM [order_mgmt].[order_combo_item] ci WHERE ci.order_id = o.order_id AND ci.combo_id = ?)
            ORDER BY o.order_id
        """;
        long total = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, maxTID);
            ps.setInt(2, singleId);
            ps.setInt(3, comboId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int orderId = rs.getInt("order_id");
                long uSingle = getItemUtil(conn, orderId, singleId, true);
                long uCombo = getItemUtil(conn, orderId, comboId, false);
                long orderTotal = uSingle + uCombo;
                total += orderTotal;
                System.out.println("  Order " + orderId + ": " + singleId + "=" + uSingle + ", cb" + comboId + "=" + uCombo + " → " + orderTotal);
            }
        }
        return total;
    }
    
    // Query pattern {single1, single2}
    static long queryPattern2Singles(Connection conn, int single1, int single2, int maxTID) throws Exception {
        String sql = """
            SELECT o.order_id
            FROM [order_mgmt].[order] o
            WHERE o.order_status = 'Completed' AND o.actual_delivery_date IS NOT NULL
              AND o.order_id <= ?
              AND EXISTS (SELECT 1 FROM [order_mgmt].[order_single_item] si WHERE si.order_id = o.order_id AND si.product_variant_id = ?)
              AND EXISTS (SELECT 1 FROM [order_mgmt].[order_single_item] si WHERE si.order_id = o.order_id AND si.product_variant_id = ?)
            ORDER BY o.order_id
        """;
        long total = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, maxTID);
            ps.setInt(2, single1);
            ps.setInt(3, single2);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int orderId = rs.getInt("order_id");
                long u1 = getItemUtil(conn, orderId, single1, true);
                long u2 = getItemUtil(conn, orderId, single2, true);
                long orderTotal = u1 + u2;
                total += orderTotal;
                System.out.println("  Order " + orderId + ": " + single1 + "=" + u1 + ", " + single2 + "=" + u2 + " → " + orderTotal);
            }
        }
        return total;
    }
    
    // Helper: get utility of item in order
    static long getItemUtil(Connection conn, int orderId, int itemId, boolean isSingle) throws Exception {
        String sql = isSingle 
            ? "SELECT quantity * unit_profit as util FROM [order_mgmt].[order_single_item] WHERE order_id = ? AND product_variant_id = ?"
            : "SELECT quantity * unit_profit as util FROM [order_mgmt].[order_combo_item] WHERE order_id = ? AND combo_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("util");
        }
        return 0;
    }
}
