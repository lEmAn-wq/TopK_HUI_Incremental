import io.github.cdimascio.dotenv.Dotenv;
import java.io.*;
import java.sql.*;
import java.util.*;

public class ITHUI_IO {

    // --- CẤU HÌNH CONFIG ---
    public static class Config {
        public int k;
        public int lastTID;
        public List<String> existingHUI = new ArrayList<>();
    }

    // 1. Đọc file output.txt
    public static Config readConfigFile(String path) {
        Config cfg = new Config();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("k")) {
                    cfg.k = Integer.parseInt(line.split("=")[1].trim());
                } else if (line.startsWith("last_TID")) {
                    cfg.lastTID = Integer.parseInt(line.split("=")[1].trim());
                }
            }
            System.out.println(">> Đã đọc Config: k=" + cfg.k + ", last_TID=" + cfg.lastTID);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cfg;
    }

    // 2. Ghi kết quả lại vào output.txt
    public static void writeConfigFile(String path, Config cfg, PriorityQueue<Pattern> topK, ITHUIState state) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("k = " + cfg.k);
            pw.println("last_TID = " + cfg.lastTID); // Update ID mới nhất
            pw.println("HUI:");
            
            // In Top-k từ Queue ra file
            // Lưu ý: Queue đang lưu thằng nhỏ nhất ở đầu, nên ta lấy ra sẽ bị ngược.
            // Cần cho vào Stack hoặc List để in từ lớn đến bé cho đẹp.
            List<Pattern> results = new ArrayList<>(topK);
            results.sort((p1, p2) -> Long.compare(p2.utility, p1.utility)); // Sort giảm dần

            for (Pattern p : results) {
                // Dịch ngược ID nội bộ (0, 1...) về ID gốc ("cb1", "101"...)
                StringBuilder sb = new StringBuilder("{");
                for (int i : p.items) {
                    sb.append(state.intToStringMap.get(i)).append(" ");
                }
                sb.append("}");
                pw.println(sb.toString().trim() + " : " + p.utility);
            }
            System.out.println(">> Đã ghi kết quả ra file " + path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 3. Kết nối SQL Server & Lấy dữ liệu mới
    public static List<TransactionTuple> fetchIncrementalData(int lastTID) {
        List<TransactionTuple> data = new ArrayList<>();
        
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String dbUrl = dotenv.get("DB_CONNECTION_STRING");

        // Kiểm tra xem có lấy được chuỗi không
        if (dbUrl == null || dbUrl.isEmpty()) {
            System.out.println("⚠️ Lỗi: Không tìm thấy DB_CONNECTION_STRING trong file .env");
            return data;
        }

        // Câu lệnh SQL "thần thánh": Gộp bảng đơn và bảng combo, xử lý prefix 'cb'
        String sql = 
            "SELECT si.order_id, CAST(si.product_variant_id AS VARCHAR) as item_id, (si.quantity * si.unit_profit) as utility " +
            "FROM [order_mgmt].[order_single_item] si " +
            "JOIN [order_mgmt].[order] o ON si.order_id = o.order_id " +
            "WHERE si.order_id > ? AND o.order_status = 'Completed' AND o.actual_delivery_date IS NOT NULL " + // <--- THÊM ĐIỀU KIỆN NÀY
            
            "UNION ALL " +
            
            "SELECT ci.order_id, 'cb' + CAST(ci.combo_id AS VARCHAR) as item_id, (ci.quantity * ci.unit_profit) as utility " +
            "FROM [order_mgmt].[order_combo_item] ci " +
            "JOIN [order_mgmt].[order] o ON ci.order_id = o.order_id " +
            "WHERE ci.order_id > ? AND o.order_status = 'Completed' AND o.actual_delivery_date IS NOT NULL " + // <--- THÊM ĐIỀU KIỆN NÀY
            
            "ORDER BY order_id ASC";

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            System.out.println(">> Đang kết nối đến SQL Server...");
            pstmt.setInt(1, lastTID);
            pstmt.setInt(2, lastTID);
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                data.add(new TransactionTuple(
                    rs.getInt("order_id"),
                    rs.getString("item_id"),
                    rs.getInt("utility")
                ));
            }
            System.out.println(">> Đã tải " + data.size() + " dòng dữ liệu mới từ SQL.");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("⚠️ Lỗi kết nối SQL! Kiểm tra lại file .env");
        }
        return data;
    }

    // Class phụ để hứng dữ liệu thô
    static class TransactionTuple {
        int tid; String itemStr; int utility;
        public TransactionTuple(int t, String i, int u) { tid=t; itemStr=i; utility=u; }
    }
}