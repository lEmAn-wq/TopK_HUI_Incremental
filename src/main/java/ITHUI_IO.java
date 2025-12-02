import io.github.cdimascio.dotenv.Dotenv;
import java.io.*;
import java.sql.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ITHUI_IO {

    public static class Config {
        public int k;
        public int lastTID;
    }

    public static Config readConfigFile(String path) {
        Config cfg = new Config();
        try {
            File file = new File(path);
            if (!file.exists()) return cfg;
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("k")) cfg.k = Integer.parseInt(line.split("=")[1].trim());
                else if (line.startsWith("last_TID")) cfg.lastTID = Integer.parseInt(line.split("=")[1].trim());
                if (line.startsWith("#")) break;
            }
            br.close();
            System.out.println(">> Config: k=" + cfg.k + ", last_TID=" + cfg.lastTID);
        } catch (Exception e) { e.printStackTrace(); }
        return cfg;
    }

    public static void writeConfigFile(String path, Config cfg, List<Pattern> results, ITHUIState state) {
        try {
            File file = new File(path);
            List<String> oldHistory = new ArrayList<>();
            if (file.exists()) {
                List<String> lines = Files.readAllLines(file.toPath());
                boolean isHistoryStart = false;
                for (String line : lines) {
                    if (line.startsWith("# ====================")) isHistoryStart = true;
                    if (isHistoryStart) oldHistory.add(line);
                }
            }

            StringBuilder newBlock = new StringBuilder();
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            
            newBlock.append("# ============================================================\n");
            newBlock.append("# FINISHED AT: ").append(dtf.format(LocalDateTime.now())).append("\n");
            newBlock.append("# DATASET: Incremental Run (TID <= ").append(cfg.lastTID).append(")\n");
            newBlock.append("# ============================================================\n");

            if (results.isEmpty()) {
                newBlock.append("(No HUI found)\n");
            } else {
                long lastUtil = -1;
                int rank = 0;
                for (Pattern p : results) {
                    // Logic in Dense Rank: Nếu giá trị đổi thì tăng Rank
                    if (p.utility != lastUtil) {
                        rank++;
                        lastUtil = p.utility;
                        newBlock.append("\n[RANK ").append(rank).append("] Utility: ").append(p.utility).append("\n");
                    }
                    StringBuilder sb = new StringBuilder(" - {");
                    for (int i : p.items) {
                        sb.append(state.intToStringMap.get(i)).append(" ");
                    }
                    sb.append("}");
                    newBlock.append(sb.toString()).append("\n");
                }
            }
            newBlock.append("\n");

            PrintWriter pw = new PrintWriter(new FileWriter(path));
            pw.println("# --- GLOBAL CONFIG (Cập nhật mới nhất) ---");
            pw.println("k = " + cfg.k);
            pw.println("last_TID = " + cfg.lastTID);
            pw.println();
            pw.print(newBlock.toString()); // In block mới
            for (String line : oldHistory) pw.println(line); // In lịch sử cũ
            pw.close();
            
            System.out.println(">> Đã ghi output.txt theo format Dense Rank.");
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static List<TransactionTuple> fetchIncrementalData(int lastTID) {
        List<TransactionTuple> data = new ArrayList<>();
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String dbUrl = dotenv.get("DB_CONNECTION_STRING");
        if (dbUrl == null) return data;

        // Câu query gộp Order Single & Combo
        String sql = "SELECT si.order_id, CAST(si.product_variant_id AS VARCHAR) as item_id, (si.quantity * si.unit_profit) as utility FROM [order_mgmt].[order_single_item] si JOIN [order_mgmt].[order] o ON si.order_id = o.order_id WHERE si.order_id > ? AND o.order_status = 'Completed' AND o.actual_delivery_date IS NOT NULL UNION ALL SELECT ci.order_id, 'cb' + CAST(ci.combo_id AS VARCHAR) as item_id, (ci.quantity * ci.unit_profit) as utility FROM [order_mgmt].[order_combo_item] ci JOIN [order_mgmt].[order] o ON ci.order_id = o.order_id WHERE ci.order_id > ? AND o.order_status = 'Completed' AND o.actual_delivery_date IS NOT NULL ORDER BY order_id ASC";

        try (Connection conn = DriverManager.getConnection(dbUrl); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, lastTID); pstmt.setInt(2, lastTID);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) data.add(new TransactionTuple(rs.getInt("order_id"), rs.getString("item_id"), rs.getInt("utility")));
            System.out.println(">> Fetch SQL: " + data.size() + " rows.");
        } catch (Exception e) { e.printStackTrace(); }
        return data;
    }

    static class TransactionTuple {
        int tid; String itemStr; int utility;
        public TransactionTuple(int t, String i, int u) { tid=t; itemStr=i; utility=u; }
    }
}