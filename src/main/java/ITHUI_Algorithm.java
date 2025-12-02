import it.unimi.dsi.fastutil.ints.*;
import java.io.*;
import java.util.*;

public class ITHUI_Algorithm {
    
    static int K_VALUE = 0;
    static HashMap<Integer, Long> itemTotalUtilityMap = new HashMap<>();

    public static void main(String[] args) {
        String configFile = "output.txt";
        String binaryFile = "ithui_state.bin";

        // 1. Đọc Config
        ITHUI_IO.Config config = ITHUI_IO.readConfigFile(configFile);
        K_VALUE = config.k;
        
        // 2. Warm-Start: Lấy ngưỡng từ lần chạy trước
        ITHUIState state = loadState(binaryFile);
        long startMinUtil = state.lastMinUtil; 
        System.out.println(">> [Warm-Start] MinUtil từ lần trước: " + startMinUtil);

        // 3. Lấy dữ liệu mới
        List<ITHUI_IO.TransactionTuple> newData = ITHUI_IO.fetchIncrementalData(config.lastTID);
        if (newData.isEmpty()) {
            System.out.println(">> Không có dữ liệu mới. Kết thúc.");
            return;
        }

        // 4. Insertion Phase: Cập nhật Global Lists
        int maxTID = config.lastTID;
        HashMap<Integer, Long> transactionUtilityMap = new HashMap<>();
        
        // Tính Transaction Utility
        for (ITHUI_IO.TransactionTuple t : newData) {
            transactionUtilityMap.put(t.tid, transactionUtilityMap.getOrDefault(t.tid, 0L) + t.utility);
            if (t.tid > maxTID) maxTID = t.tid;
        }
        config.lastTID = maxTID;

        // Cập nhật List
        for (ITHUI_IO.TransactionTuple t : newData) {
            if (!state.stringToIntMap.containsKey(t.itemStr)) {
                int newId = state.nextInternalId++;
                state.stringToIntMap.put(t.itemStr, newId);
                state.intToStringMap.put(newId, t.itemStr);
                state.globalLists.put(newId, new GlobalUtilityList(newId));
            }
            int internalId = state.stringToIntMap.get(t.itemStr);
            GlobalUtilityList list = state.globalLists.get(internalId);
            list.tids.add(t.tid);
            list.utilities.add(t.utility);
            list.sumIU += t.utility;
            list.twu += transactionUtilityMap.get(t.tid);
        }

        // Cache Utility cho LIU-LB
        itemTotalUtilityMap.clear();
        for (GlobalUtilityList list : state.globalLists.values()) {
            itemTotalUtilityMap.put(list.itemInternalId, list.sumIU);
        }

        // =========================================================
        // GIAI ĐOẠN 1: NÂNG NGƯỠNG (THRESHOLD RAISING)
        // Dùng GlobalThresholdQueue (Strict Top-k)
        // =========================================================
        
        GlobalThresholdQueue thresholdQueue = new GlobalThresholdQueue(K_VALUE, startMinUtil);

        // A. Chiến lược RIU: Duyệt tuần tự -> Nạp vào Queue
        runStrategy_RIU(state, thresholdQueue);

        // B. Sắp xếp & Restructuring
        List<GlobalUtilityList> sortedLists = new ArrayList<>(state.globalLists.values());
        sortedLists.sort((a, b) -> Long.compare(a.twu, b.twu)); 
        // Dùng minUtil hiện tại của Queue để cắt tỉa việc tính RU
        runRestructuring(sortedLists, thresholdQueue.getMinUtil());

        // C. Chiến lược LIU
        runStrategy_LIU(sortedLists, thresholdQueue);
        
        // CHỐT NGƯỠNG
        long miningThreshold = thresholdQueue.getMinUtil();
        System.out.println(">> [Phase 1 End] Ngưỡng chốt để Mining: " + miningThreshold);
        thresholdQueue = null; // Giải phóng

        // =========================================================
        // GIAI ĐOẠN 2: KHAI PHÁ (MINING)
        // Dùng TopKResultList (Dense Rank)
        // =========================================================
        
        TopKResultList resultList = new TopKResultList(K_VALUE, miningThreshold);
        
        List<GlobalUtilityList> seedLists = new ArrayList<>();
        
        // Cần cập nhật lại minU mới nhất từ resultList mỗi khi thêm item
        // (Dù lúc đầu nó bằng miningThreshold nhưng sau khi add item đơn nó có thể tăng lên)
        
        for (GlobalUtilityList list : sortedLists) {
            // Lấy ngưỡng hiện tại của ResultList để lọc
            long currentMin = resultList.getMinUtil(); 

            // Cắt tỉa cơ bản
            if (list.twu < currentMin) continue;

            // [FIX QUAN TRỌNG] Thêm Item đơn lẻ vào kết quả nếu đủ ngưỡng
            if (list.sumIU >= currentMin) {
                IntArrayList pattern = new IntArrayList();
                pattern.add(list.itemInternalId);
                resultList.add(new Pattern(pattern, list.sumIU));
            }

            // Kiểm tra khả năng mở rộng (Upper Bound)
            if (list.sumIU + list.sumRU >= currentMin) {
                seedLists.add(list);
            }
        }
        
        // Chạy Mining DFS cho các mẫu phối hợp (2 item trở lên)
        runMiningDFS(null, seedLists, resultList);

        // =========================================================
        // GIAI ĐOẠN 3: LƯU TRỮ
        // =========================================================
        
        state.lastMinUtil = resultList.getMinUtil(); // Lưu ngưỡng Dense Rank cho lần sau
        List<Pattern> finalResults = resultList.getResults();
        
        saveState(state, binaryFile);
        ITHUI_IO.writeConfigFile(configFile, config, finalResults, state);
    }

    // ---------------------------------------------------------
    // CÁC CHIẾN LƯỢC
    // ---------------------------------------------------------

    static void runStrategy_RIU(ITHUIState state, GlobalThresholdQueue queue) {
        // Duyệt tuần tự từng item, kiểm tra với minUtil hiện tại
        for (GlobalUtilityList list : state.globalLists.values()) {
            queue.add(list.sumIU);
        }
        System.out.println(">> [RIU] Ngưỡng: " + queue.getMinUtil());
    }

    static void runRestructuring(List<GlobalUtilityList> sortedLists, long minU) {
        HashMap<Integer, Long> tempArray = new HashMap<>(); 
        for (int i = sortedLists.size() - 1; i >= 0; i--) {
            GlobalUtilityList list = sortedLists.get(i);
            if (list.twu < minU) continue;
            list.resetRU();
            for (int j = 0; j < list.tids.size(); j++) {
                int tid = list.tids.getInt(j);
                long util = list.utilities.getInt(j);
                long currentRU = tempArray.getOrDefault(tid, 0L);
                list.remainingUtilities.set(j, (int)currentRU);
                tempArray.put(tid, currentRU + util);
            }
        }
        System.out.println(">> [Restructuring] Hoàn tất.");
    }

    static void runStrategy_LIU(List<GlobalUtilityList> sortedLists, GlobalThresholdQueue queue) {
        System.out.println(">> [LIU] Đang chạy...");
        long minU = queue.getMinUtil();
        
        // ... (Logic xây dựng ma trận LIU giữ nguyên như cũ) ...
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

        // LIU-E
        for (Long val : liuMatrix.values()) queue.add(val);
        System.out.println(">> [LIU-E] Ngưỡng: " + queue.getMinUtil());

        // LIU-LB (Recursive)
        for (Map.Entry<String, Long> entry : liuMatrix.entrySet()) {
            long baseVal = entry.getValue();
            if (baseVal < queue.getMinUtil()) continue;
            IntArrayList mids = liuMiddleItems.get(entry.getKey());
            if (mids != null && !mids.isEmpty()) {
                recursiveLIULB(baseVal, mids, 0, 0, queue);
            }
        }
        System.out.println(">> [LIU-LB] Ngưỡng: " + queue.getMinUtil());
    }

    static void recursiveLIULB(long currentVal, IntArrayList mids, int startIdx, int removedCount, GlobalThresholdQueue queue) {
        queue.add(currentVal); 
        if (removedCount >= 3 || startIdx >= mids.size()) return;
        
        long threshold = queue.getMinUtil();
        for (int i = startIdx; i < mids.size(); i++) {
            int itemID = mids.getInt(i);
            long newVal = currentVal - itemTotalUtilityMap.getOrDefault(itemID, 0L);
            if (newVal < threshold) continue; 
            recursiveLIULB(newVal, mids, i + 1, removedCount + 1, queue);
        }
    }

    static void runMiningDFS(GlobalUtilityList parentList, List<GlobalUtilityList> siblings, TopKResultList resultList) {
        long minU = resultList.getMinUtil();
        
        for (int i = 0; i < siblings.size(); i++) {
            GlobalUtilityList prefixList = siblings.get(i);
            List<GlobalUtilityList> childLists = new ArrayList<>();
            
            for (int j = i + 1; j < siblings.size(); j++) {
                GlobalUtilityList extensionList = siblings.get(j);
                GlobalUtilityList newList = constructUtilityList(prefixList, extensionList, parentList);
                
                if (newList.sumIU >= minU) {
                    IntArrayList newPatternItems = new IntArrayList();
                    if (parentList != null) newPatternItems.addAll(parentList.patternItems);
                    else newPatternItems.add(prefixList.itemInternalId);
                    newPatternItems.add(extensionList.itemInternalId);
                    
                    resultList.add(new Pattern(newPatternItems, newList.sumIU));
                    minU = resultList.getMinUtil(); 
                }
                
                if (newList.sumIU + newList.sumRU >= minU) {
                    childLists.add(newList);
                }
            }
            if (!childLists.isEmpty()) runMiningDFS(prefixList, childLists, resultList);
        }
    }
    
    // ... (constructUtilityList, saveState, loadState giữ nguyên) ...
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