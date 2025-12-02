import it.unimi.dsi.fastutil.ints.*;
import java.io.*;
import java.util.*;

public class ITHUI_Algorithm {
    
    static int K_VALUE = 0;
    static HashMap<Integer, Long> itemTotalUtilityMap = new HashMap<>();
    static boolean DEBUG = true; // Bật/tắt debug log

    public static void main(String[] args) {
        String configFile = "output.txt";
        String binaryFile = "ithui_state.bin";

        // 1. Đọc Config
        ITHUI_IO.Config config = ITHUI_IO.readConfigFile(configFile);
        K_VALUE = config.k;
        debug("============================================");
        debug("KHỞI ĐỘNG THUẬT TOÁN ITHUI");
        debug("============================================");
        debug("[CONFIG] k=" + config.k + ", last_TID=" + config.lastTID);
        
        // 2. Warm-Start
        ITHUIState state = loadState(binaryFile);
        long startMinUtil = state.lastMinUtil; 
        System.out.println(">> [Warm-Start] MinUtil từ lần trước: " + startMinUtil);
        debug("[WARM-START] Số item đã có: " + state.globalLists.size());

        // 3. Fetch Data
        List<ITHUI_IO.TransactionTuple> newData = ITHUI_IO.fetchIncrementalData(config.lastTID);
        if (newData.isEmpty()) {
            System.out.println(">> Không có dữ liệu mới. Kết thúc.");
            return;
        }
        debug("[DATA] Tải được " + newData.size() + " rows dữ liệu mới");

        // 4. Update Global Lists
        int maxTID = config.lastTID;
        HashMap<Integer, Long> transactionUtilityMap = new HashMap<>();
        for (ITHUI_IO.TransactionTuple t : newData) {
            transactionUtilityMap.put(t.tid, transactionUtilityMap.getOrDefault(t.tid, 0L) + t.utility);
            if (t.tid > maxTID) maxTID = t.tid;
        }
        config.lastTID = maxTID;
        debug("[DATA] Số giao dịch mới: " + transactionUtilityMap.size() + " (TID từ " + (config.lastTID - transactionUtilityMap.size() + 1) + " đến " + maxTID + ")");

        for (ITHUI_IO.TransactionTuple t : newData) {
            if (!state.stringToIntMap.containsKey(t.itemStr)) {
                int newId = state.nextInternalId++;
                state.stringToIntMap.put(t.itemStr, newId);
                state.intToStringMap.put(newId, t.itemStr);
                state.globalLists.put(newId, new GlobalUtilityList(newId));
                debug("[NEW-ITEM] Thêm item mới: " + t.itemStr + " (id=" + newId + ")");
            }
            int internalId = state.stringToIntMap.get(t.itemStr);
            GlobalUtilityList list = state.globalLists.get(internalId);
            list.tids.add(t.tid);
            list.utilities.add(t.utility);
            list.sumIU += t.utility;
            list.twu += transactionUtilityMap.get(t.tid);
        }

        // =========================================================
        // GIAI ĐOẠN 1: NÂNG NGƯỠNG (THRESHOLD RAISING)
        // =========================================================
        debug("");
        debug("============================================");
        debug("GIAI ĐOẠN 1: NÂNG NGƯỠNG (THRESHOLD RAISING)");
        debug("============================================");
        
        GlobalThresholdQueue thresholdQueue = new GlobalThresholdQueue(K_VALUE, startMinUtil);
        debug("[QUEUE] Khởi tạo ThresholdQueue: k=" + K_VALUE + ", minUtil=" + startMinUtil);

        // Cache Utility
        itemTotalUtilityMap.clear();
        for (GlobalUtilityList list : state.globalLists.values()) {
            itemTotalUtilityMap.put(list.itemInternalId, list.sumIU);
        }
        
        // In danh sách item và utility
        debug("[ITEMS] Danh sách item hiện có:");
        for (Map.Entry<Integer, GlobalUtilityList> e : state.globalLists.entrySet()) {
            String name = state.intToStringMap.get(e.getKey());
            GlobalUtilityList list = e.getValue();
            debug("   - " + name + ": sumIU=" + list.sumIU + ", TWU=" + list.twu + ", #trans=" + list.tids.size());
        }

        // A. RIU
        debug("");
        debug("[RIU] Bắt đầu chiến lược RIU...");
        runStrategy_RIU(state, thresholdQueue);

        // B. Restructuring
        List<GlobalUtilityList> sortedLists = new ArrayList<>(state.globalLists.values());
        sortedLists.sort((a, b) -> Long.compare(a.twu, b.twu)); 
        debug("");
        debug("[RESTRUCTURING] Sắp xếp item theo TWU tăng dần:");
        for (GlobalUtilityList list : sortedLists) {
            debug("   - " + state.intToStringMap.get(list.itemInternalId) + " (TWU=" + list.twu + ")");
        }
        runRestructuring(sortedLists, thresholdQueue.getMinUtil());

        // C. LIU
        debug("");
        debug("[LIU] Bắt đầu chiến lược LIU...");
        runStrategy_LIU(sortedLists, thresholdQueue);
        
        long miningThreshold = thresholdQueue.getMinUtil();
        System.out.println(">> [Phase 1 End] Ngưỡng chốt để Mining: " + miningThreshold);
        debug("[QUEUE] Giá trị trong queue: " + thresholdQueue.getQueueValues());
        thresholdQueue = null;

        // =========================================================
        // GIAI ĐOẠN 2: KHAI PHÁ (MINING)
        // =========================================================
        debug("");
        debug("============================================");
        debug("GIAI ĐOẠN 2: KHAI PHÁ (MINING DFS)");
        debug("============================================");
        debug("[MINING] Ngưỡng ban đầu: " + miningThreshold);
        
        TopKResultList resultList = new TopKResultList(K_VALUE, miningThreshold);
        
        // [FIX QUAN TRỌNG]: Tạo danh sách item tham gia mining
        List<GlobalUtilityList> seedLists = new ArrayList<>();
        
        debug("");
        debug("[SINGLE-ITEMS] Xử lý item đơn lẻ:");
        for (GlobalUtilityList list : sortedLists) {
            String itemName = state.intToStringMap.get(list.itemInternalId);
            
            // 1. Chỉ loại bỏ nếu TWU quá thấp (không thể làm Prefix lẫn Extension)
            if (list.twu < miningThreshold) {
                debug("   - " + itemName + ": TWU=" + list.twu + " < " + miningThreshold + " → SKIP (TWU thấp)");
                continue;
            }
            
            // 2. Thêm vào seedLists để làm nguyên liệu cho DFS
            seedLists.add(list);

            // 3. [FIX] Thêm chính Item đơn lẻ này vào kết quả (nếu đủ ngưỡng)
            // (Lưu ý: resultList tự lo việc kiểm tra minUtil động)
            if (list.sumIU >= resultList.getMinUtil()) {
                IntArrayList pattern = new IntArrayList();
                pattern.add(list.itemInternalId);
                resultList.add(new Pattern(pattern, list.sumIU));
                debug("   - " + itemName + ": sumIU=" + list.sumIU + " >= " + resultList.getMinUtil() + " → THÊM VÀO TOP-K ✓");
                debug("     [TOP-K] Sau khi thêm: " + resultList.size() + " rank, minUtil=" + resultList.getMinUtil());
            } else {
                debug("   - " + itemName + ": sumIU=" + list.sumIU + " < " + resultList.getMinUtil() + " → Không đủ ngưỡng");
            }
        }
        
        debug("");
        debug("[MINING-DFS] Bắt đầu khai phá mở rộng...");
        debug("[MINING-DFS] Số seed items: " + seedLists.size());
        
        // Chạy Mining
        runMiningDFS(null, seedLists, resultList, state);

        // =========================================================
        // GIAI ĐOẠN 3: LƯU TRỮ
        // =========================================================
        debug("");
        debug("============================================");
        debug("GIAI ĐOẠN 3: KẾT QUẢ CUỐI CÙNG");
        debug("============================================");
        
        state.lastMinUtil = resultList.getMinUtil();
        List<Pattern> finalResults = resultList.getResults();
        
        debug("[RESULT] Tổng số rank: " + resultList.size());
        debug("[RESULT] minUtil cuối: " + resultList.getMinUtil());
        debug("[RESULT] Danh sách Top-K HUI:");
        int rank = 1;
        for (Pattern p : finalResults) {
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < p.items.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(state.intToStringMap.get(p.items.getInt(i)));
            }
            sb.append("}");
            debug("   #" + rank + ": " + sb.toString() + " = " + p.utility);
            rank++;
        }
        
        saveState(state, binaryFile);
        ITHUI_IO.writeConfigFile(configFile, config, finalResults, state);
        debug("");
        debug(">> Hoàn tất!");
    }
    
    // Hàm debug helper
    static void debug(String msg) {
        if (DEBUG) System.out.println("[DEBUG] " + msg);
    }

    // ---------------------------------------------------------
    // CÁC CHIẾN LƯỢC
    // ---------------------------------------------------------

    static void runStrategy_RIU(ITHUIState state, GlobalThresholdQueue queue) {
        for (GlobalUtilityList list : state.globalLists.values()) {
            String name = state.intToStringMap.get(list.itemInternalId);
            long oldMin = queue.getMinUtil();
            queue.add(list.sumIU);
            long newMin = queue.getMinUtil();
            if (newMin != oldMin) {
                debug("[RIU] " + name + ": sumIU=" + list.sumIU + " → Ngưỡng nâng từ " + oldMin + " lên " + newMin);
            } else {
                debug("[RIU] " + name + ": sumIU=" + list.sumIU + " (ngưỡng giữ nguyên " + newMin + ")");
            }
        }
        System.out.println(">> [RIU] Ngưỡng: " + queue.getMinUtil());
    }

    static void runRestructuring(List<GlobalUtilityList> sortedLists, long minU) {
        HashMap<Integer, Long> tempArray = new HashMap<>(); 
        for (int i = sortedLists.size() - 1; i >= 0; i--) {
            GlobalUtilityList list = sortedLists.get(i);
            if (list.twu < minU) continue;
            list.resetRU();
            long sumRU = 0; // Tính tổng RU
            for (int j = 0; j < list.tids.size(); j++) {
                int tid = list.tids.getInt(j);
                long util = list.utilities.getInt(j);
                long currentRU = tempArray.getOrDefault(tid, 0L);
                list.remainingUtilities.set(j, (int)currentRU);
                sumRU += currentRU; // Cộng dồn RU
                tempArray.put(tid, currentRU + util);
            }
            list.sumRU = sumRU; // Gán sumRU cho item
            debug("[RESTRUCTURING] " + list.itemInternalId + ": sumRU=" + sumRU);
        }
        System.out.println(">> [Restructuring] Hoàn tất.");
    }

    static void runStrategy_LIU(List<GlobalUtilityList> sortedLists, GlobalThresholdQueue queue) {
        System.out.println(">> [LIU] Đang chạy...");
        long minU = queue.getMinUtil();
        
        HashMap<Integer, ArrayList<int[]>> tempTransactions = new HashMap<>();
        HashMap<Integer, Integer> itemRankMap = new HashMap<>();
        for(int i=0; i<sortedLists.size(); i++) itemRankMap.put(sortedLists.get(i).itemInternalId, i);

        for (GlobalUtilityList list : sortedLists) {
            if (list.twu < minU) continue;
            for (int i = 0; i < list.tids.size(); i++) {
                tempTransactions.computeIfAbsent(list.tids.getInt(i), k -> new ArrayList<>())
                    .add(new int[]{list.itemInternalId, list.utilities.getInt(i)});
            }
        }

        HashMap<String, Long> liuMatrix = new HashMap<>(); 
        HashMap<String, IntArrayList> liuMiddleItems = new HashMap<>();

        for (ArrayList<int[]> trans : tempTransactions.values()) {
            trans.sort(Comparator.comparingInt(a -> itemRankMap.get(a[0])));
            for (int i = 0; i < trans.size(); i++) {
                int[] startItem = trans.get(i);
                long currentChainUtil = startItem[1]; 
                for (int j = i + 1; j < trans.size(); j++) {
                    int[] endItem = trans.get(j);
                    int[] prevItem = trans.get(j-1);
                    if (itemRankMap.get(endItem[0]) != itemRankMap.get(prevItem[0]) + 1) break; 
                    currentChainUtil += endItem[1];
                    String key = startItem[0] + "_" + endItem[0];
                    liuMatrix.put(key, liuMatrix.getOrDefault(key, 0L) + currentChainUtil); 
                    if (!liuMiddleItems.containsKey(key)) {
                        IntArrayList mids = new IntArrayList();
                        for(int k = i + 1; k < j; k++) mids.add(trans.get(k)[0]);
                        liuMiddleItems.put(key, mids);
                    }
                }
            }
        }

        debug("[LIU-E] Số chuỗi tìm thấy: " + liuMatrix.size());
        int liuECount = 0;
        for (Map.Entry<String, Long> entry : liuMatrix.entrySet()) {
            long oldMin = queue.getMinUtil();
            queue.add(entry.getValue());
            long newMin = queue.getMinUtil();
            if (newMin != oldMin) {
                debug("[LIU-E] Chuỗi " + entry.getKey() + ": utility=" + entry.getValue() + " → Ngưỡng nâng từ " + oldMin + " lên " + newMin);
                liuECount++;
            }
        }
        debug("[LIU-E] Số chuỗi làm nâng ngưỡng: " + liuECount);
        System.out.println(">> [LIU-E] Ngưỡng: " + queue.getMinUtil());

        debug("[LIU-LB] Đang chạy...");
        int liuLBCount = 0;
        for (Map.Entry<String, Long> entry : liuMatrix.entrySet()) {
            long baseVal = entry.getValue();
            if (baseVal < queue.getMinUtil()) continue;
            IntArrayList mids = liuMiddleItems.get(entry.getKey());
            if (mids != null && !mids.isEmpty()) {
                liuLBCount += recursiveLIULB(baseVal, mids, 0, 0, queue);
            }
        }
        debug("[LIU-LB] Số lower-bound làm nâng ngưỡng: " + liuLBCount);
        System.out.println(">> [LIU-LB] Ngưỡng: " + queue.getMinUtil());
    }

    static int recursiveLIULB(long currentVal, IntArrayList mids, int startIdx, int removedCount, GlobalThresholdQueue queue) {
        long oldMin = queue.getMinUtil();
        queue.add(currentVal);
        int count = (queue.getMinUtil() != oldMin) ? 1 : 0;
        
        if (removedCount >= 3 || startIdx >= mids.size()) return count;
        
        long threshold = queue.getMinUtil();
        for (int i = startIdx; i < mids.size(); i++) {
            int itemID = mids.getInt(i);
            long newVal = currentVal - itemTotalUtilityMap.getOrDefault(itemID, 0L);
            if (newVal < threshold) continue; 
            count += recursiveLIULB(newVal, mids, i + 1, removedCount + 1, queue);
        }
        return count;
    }

    static void runMiningDFS(GlobalUtilityList parentList, List<GlobalUtilityList> siblings, TopKResultList resultList, ITHUIState state) {
        long minU = resultList.getMinUtil(); 
        
        for (int i = 0; i < siblings.size(); i++) {
            GlobalUtilityList prefixList = siblings.get(i);
            String prefixName = state.intToStringMap.get(prefixList.itemInternalId);
            
            // [FIX] Kiểm tra điều kiện UB ở đây để quyết định có ĐI SÂU hay không
            // Nhưng KHÔNG loại prefixList khỏi danh sách siblings của người khác
            long upperBound = prefixList.sumIU + prefixList.sumRU;
            if (upperBound < minU) {
                debug("[DFS] Prefix " + prefixName + ": UB=" + upperBound + " < " + minU + " → Cắt tỉa");
                continue;
            }

            List<GlobalUtilityList> childLists = new ArrayList<>();
            for (int j = i + 1; j < siblings.size(); j++) {
                GlobalUtilityList extensionList = siblings.get(j);
                String extName = state.intToStringMap.get(extensionList.itemInternalId);
                GlobalUtilityList newList = constructUtilityList(prefixList, extensionList, parentList);
                
                // [FIX] Xây dựng pattern items đúng cách
                IntArrayList newPatternItems = new IntArrayList();
                newPatternItems.addAll(prefixList.patternItems);  // Copy từ prefix (đã chứa đầy đủ path)
                newPatternItems.add(extensionList.itemInternalId); // Thêm extension
                newList.patternItems = newPatternItems; // Gán cho newList để dùng trong recursive
                
                // Tạo tên pattern để debug
                StringBuilder patternName = new StringBuilder("{");
                for (int k = 0; k < newPatternItems.size(); k++) {
                    if (k > 0) patternName.append(", ");
                    patternName.append(state.intToStringMap.get(newPatternItems.getInt(k)));
                }
                patternName.append("}");
                
                if (newList.sumIU >= minU) {
                    resultList.add(new Pattern(newPatternItems, newList.sumIU));
                    debug("[DFS] " + patternName + ": sumIU=" + newList.sumIU + " >= " + minU + " → THÊM VÀO TOP-K ✓");
                    minU = resultList.getMinUtil(); // Cập nhật minU
                    debug("     [TOP-K] Sau khi thêm: " + resultList.size() + " rank, minUtil=" + minU);
                } else {
                    debug("[DFS] " + patternName + ": sumIU=" + newList.sumIU + " < " + minU + " → Không đủ ngưỡng");
                }
                
                long newUB = newList.sumIU + newList.sumRU;
                if (newUB >= minU) {
                    debug("[DFS] " + patternName + ": UB=" + newUB + " >= " + minU + " → Tiếp tục mở rộng");
                    childLists.add(newList);
                }
            }
            
            if (!childLists.isEmpty()) runMiningDFS(prefixList, childLists, resultList, state);
        }
    }
    
    static GlobalUtilityList constructUtilityList(GlobalUtilityList listX, GlobalUtilityList listY, GlobalUtilityList listParent) {
        GlobalUtilityList res = new GlobalUtilityList(listY.itemInternalId); 
        long sumIU = 0, sumRU = 0;
        int idxX = 0, idxY = 0, idxP = 0;
        while (idxX < listX.tids.size() && idxY < listY.tids.size()) {
            int tidX = listX.tids.getInt(idxX);
            int tidY = listY.tids.getInt(idxY);
            if (tidX == tidY) {
                int commonTID = tidX;
                long uX = listX.utilities.getInt(idxX);
                long uY = listY.utilities.getInt(idxY);
                long uP = 0;
                if (listParent != null) {
                    while (idxP < listParent.tids.size() && listParent.tids.getInt(idxP) < commonTID) idxP++;
                    if (idxP < listParent.tids.size() && listParent.tids.getInt(idxP) == commonTID) uP = listParent.utilities.getInt(idxP);
                }
                long newUtil = uX + uY - uP;
                long newRU = listY.remainingUtilities.getInt(idxY);
                res.tids.add(commonTID);
                res.utilities.add((int)newUtil);
                res.remainingUtilities.add((int)newRU);
                sumIU += newUtil; sumRU += newRU;
                idxX++; idxY++;
            } else if (tidX < tidY) idxX++; else idxY++;
        }
        res.sumIU = sumIU; res.sumRU = sumRU;
        return res;
    }
    static void saveState(ITHUIState state, String filename) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(state);
            System.out.println(">> Đã lưu trạng thái.");
        } catch(Exception e) { e.printStackTrace(); }
    }
    static ITHUIState loadState(String filename) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename))) {
            ITHUIState s = (ITHUIState) ois.readObject();
            for(GlobalUtilityList l : s.globalLists.values()) l.resetRU();
            return s;
        } catch(Exception e) { return new ITHUIState(); }
    }
}