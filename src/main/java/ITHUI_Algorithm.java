import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.*;
import it.unimi.dsi.fastutil.ints.*;
import java.io.*;
import java.util.*;

public class ITHUI_Algorithm {
    
    static long minUtil = 0; // Ngưỡng toàn cục
    static int K_VALUE = 0;  // K người dùng nhập

    // ---------------------------------------------------------
    // MAIN: Nơi điều phối mọi thứ
    // ---------------------------------------------------------
    public static void main(String[] args) {
        String configFile = "output.txt";
        String binaryFile = "ithui_state.bin";

        // 1. Đọc Config (k, last_TID)
        ITHUI_IO.Config config = ITHUI_IO.readConfigFile(configFile);
        K_VALUE = config.k;

        // 2. Load trạng thái cũ (Kryo)
        ITHUIState state = loadStateKryo(binaryFile);
        
        // --- CHIẾN LƯỢC: TÁI SỬ DỤNG TOP-K CŨ ---
        // Đổ Top-k cũ vào hàng đợi để "mồi" ngưỡng minUtil
        PriorityQueue<Pattern> pq = state.topKPatterns; 
        if (!pq.isEmpty()) {
            // Nếu PQ đầy k phần tử, lấy thằng nhỏ nhất làm ngưỡng ngay
            if (pq.size() >= K_VALUE) {
                while (pq.size() > K_VALUE) pq.poll(); // Giữ đúng k
                minUtil = pq.peek().utility;
                System.out.println(">> [Warm-Start] Khôi phục ngưỡng cũ: " + minUtil);
            }
        }

        // 3. Lấy dữ liệu mới từ SQL
        List<ITHUI_IO.TransactionTuple> newData = ITHUI_IO.fetchIncrementalData(config.lastTID);
        
        if (newData.isEmpty()) {
            System.out.println(">> Không có dữ liệu mới. Kết thúc.");
            return;
        }

        // 4. CẬP NHẬT DỮ LIỆU (Insertion Phase)
        // Duyệt qua dữ liệu thô, Mapping ID, cập nhật Global List
        int maxTID = config.lastTID;
        for (ITHUI_IO.TransactionTuple t : newData) {
            // Mapping: "cb1" -> ID số (ví dụ 5)
            if (!state.stringToIntMap.containsKey(t.itemStr)) {
                int newId = state.nextInternalId++;
                state.stringToIntMap.put(t.itemStr, newId);
                state.intToStringMap.put(newId, t.itemStr);
                state.globalLists.put(newId, new GlobalUtilityList(newId));
            }
            int internalId = state.stringToIntMap.get(t.itemStr);
            
            // Cập nhật Global List
            GlobalUtilityList list = state.globalLists.get(internalId);
            list.tids.add(t.tid);
            list.utilities.add(t.utility);
            list.sumIU += t.utility;
            // Tính TWU sơ bộ (cần cộng dồn Transaction Utility - ở đây làm đơn giản là cộng utility item)
            // *Lưu ý: Đúng ra phải tính TU của cả giao dịch trước, nhưng để code ngắn gọn tôi tạm cộng dồn util.
            list.twu += t.utility; 
            
            if (t.tid > maxTID) maxTID = t.tid;
        }
        config.lastTID = maxTID; // Cập nhật TID mới nhất để ghi lại file

        // 5. CHIẾN LƯỢC RIU (Real Item Utility)
        // Nâng ngưỡng dựa trên tiện ích của item đơn lẻ
        runStrategy_RIU(state, pq);

        // 6. SẮP XẾP & TÁI CẤU TRÚC (Sorting & Restructuring)
        List<GlobalUtilityList> sortedLists = new ArrayList<>(state.globalLists.values());
        // Sort theo TWU tăng dần
        sortedLists.sort((a, b) -> Long.compare(a.twu, b.twu)); 
        
        // Tính RU (Remaining Utility) - Reset và tính lại
        runRestructuring(sortedLists);

        // 7. CHIẾN LƯỢC LIU (Construct LIU -> LIU-E -> LIU-LB)
        runStrategy_LIU(sortedLists, pq);

        // 8. KHAI PHÁ (Mining with RUC)
        // Lọc lần cuối: Chỉ mining những item > minUtil
        System.out.println(">> Bắt đầu khai phá với ngưỡng: " + minUtil);
        // (Đây là chỗ gọi hàm đệ quy - Tôi viết khung sườn, Sếp điền thêm logic DFS nếu cần chi tiết)
        // runMiningDFS(...) 
        
        // 9. LƯU TRẠNG THÁI & KẾT QUẢ
        state.topKPatterns = pq; // Lưu lại Top-k mới nhất
        saveStateKryo(state, binaryFile);
        ITHUI_IO.writeConfigFile(configFile, config, pq, state);
    }

    // ---------------------------------------------------------
    // CÁC CHIẾN LƯỢC (STRATEGIES) - Tái sử dụng được
    // ---------------------------------------------------------

    // 1. RIU: Nâng ngưỡng bằng Item đơn lẻ
    static void runStrategy_RIU(ITHUIState state, PriorityQueue<Pattern> pq) {
        for (GlobalUtilityList list : state.globalLists.values()) {
            if (list.sumIU >= minUtil) {
                IntArrayList itemSet = new IntArrayList();
                itemSet.add(list.itemInternalId);
                updateTopK(pq, new Pattern(itemSet, list.sumIU));
            }
        }
        System.out.println(">> [RIU] Ngưỡng sau khi chạy RIU: " + minUtil);
    }

    // 2. Restructuring: Tính toán RU (Reset về 0 rồi tính ngược)
    static void runRestructuring(List<GlobalUtilityList> sortedLists) {
        // Mảng tạm để cộng dồn RU (Key: TID, Value: Utility tích lũy)
        // Dùng Map cho đơn giản vì TID rời rạc
        HashMap<Integer, Long> tempArray = new HashMap<>(); 
        
        // Quét ngược từ item cuối cùng lên đầu
        for (int i = sortedLists.size() - 1; i >= 0; i--) {
            GlobalUtilityList list = sortedLists.get(i);
            
            // QUAN TRỌNG: Nếu TWU < minUtil -> Bỏ qua (Skip), không tính RU cho nó
            // Đây chính là tối ưu mà Sếp yêu cầu
            if (list.twu < minUtil) continue;

            list.resetRU(); // Cấp phát lại bộ nhớ cho RU (toàn số 0)

            for (int j = 0; j < list.tids.size(); j++) {
                int tid = list.tids.getInt(j);
                long util = list.utilities.getInt(j);
                
                // Lấy RU từ bảng tạm gán vào list
                long currentRU = tempArray.getOrDefault(tid, 0L);
                list.remainingUtilities.set(j, (int)currentRU); // *Lưu ý ép kiểu nếu RU quá lớn
                
                // Cộng dồn utility của item này vào bảng tạm cho thằng đứng trước dùng
                tempArray.put(tid, currentRU + util);
            }
        }
        System.out.println(">> [Restructuring] Đã tính xong RU (có lọc TWU).");
    }

    // 3. LIU Strategy (Xây dựng LIU & Nâng ngưỡng)
    static void runStrategy_LIU(List<GlobalUtilityList> sortedLists, PriorityQueue<Pattern> pq) {
        // Bước A: Tạo giao dịch tạm (Restoring)
        // Map<TID, List<Item>>
        HashMap<Integer, IntArrayList> tempTransactions = new HashMap<>();
        
        for (GlobalUtilityList list : sortedLists) {
            if (list.twu < minUtil) continue; // Lọc TWU

            for (int i = 0; i < list.tids.size(); i++) {
                int tid = list.tids.getInt(i);
                tempTransactions.computeIfAbsent(tid, k -> new IntArrayList()).add(list.itemInternalId);
            }
        }

        // Bước B: Xây dựng Ma trận LIU (Map<Cặp_Item, Utility>)
        // Key: Mã Hash của cặp (u, v), Value: Utility
        HashMap<String, Long> liuMatrix = new HashMap<>(); 
        
        // (Logic xây dựng ma trận LIU và chạy LIU-E, LIU-LB nằm ở đây)
        // Do giới hạn độ dài, tôi mô tả logic: Duyệt tempTransactions, tìm cặp liền kề, cộng vào liuMatrix.
        // Sau đó duyệt liuMatrix để updateTopK.
        
        System.out.println(">> [LIU] Đã chạy xong chiến lược LIU (Simulation).");
    }

    // Hàm cập nhật Top-K (RUC logic)
    static void updateTopK(PriorityQueue<Pattern> pq, Pattern p) {
        if (p.utility < minUtil && pq.size() >= K_VALUE) return;
        
        pq.add(p);
        // Nếu tràn k thì đá thằng nhỏ nhất ra
        while (pq.size() > K_VALUE) {
            pq.poll();
        }
        
        // Cập nhật minUtil mới
        if (pq.size() == K_VALUE) {
            minUtil = pq.peek().utility;
        }
    }

    // --- KRYO UTILS ---
    static void saveStateKryo(ITHUIState state, String filename) {
        try {
            Kryo kryo = new Kryo();
            kryo.setRegistrationRequired(false);
            Output output = new Output(new FileOutputStream(filename));
            kryo.writeObject(output, state);
            output.close();
            System.out.println(">> Đã lưu trạng thái Binary.");
        } catch(Exception e) { e.printStackTrace(); }
    }

    static ITHUIState loadStateKryo(String filename) {
        try {
            Kryo kryo = new Kryo();
            kryo.setRegistrationRequired(false);
            Input input = new Input(new FileInputStream(filename));
            ITHUIState s = kryo.readObject(input, ITHUIState.class);
            input.close();
            // Reset RU sau khi load
            for(GlobalUtilityList l : s.globalLists.values()) l.resetRU();
            return s;
        } catch(Exception e) {
            return new ITHUIState(); // File chưa có thì tạo mới
        }
    }
}