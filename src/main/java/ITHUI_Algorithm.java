import it.unimi.dsi.fastutil.ints.*;
import java.io.*;
import java.util.*;

public class ITHUI_Algorithm {
    
    static long minUtil = 0; // Ngưỡng toàn cục
    static int K_VALUE = 0;  // K người dùng nhập

    static HashMap<Integer, Long> itemTotalUtilityMap = new HashMap<>();
    // ---------------------------------------------------------
    // MAIN: Nơi điều phối mọi thứ
    // ---------------------------------------------------------
    public static void main(String[] args) {
        String configFile = "output.txt";
        String binaryFile = "ithui_state.bin";

        // 1. Đọc Config (k, last_TID)
        ITHUI_IO.Config config = ITHUI_IO.readConfigFile(configFile);
        K_VALUE = config.k;

        // 2. Load trạng thái cũ
        ITHUIState state = loadState(binaryFile);
        
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

        // 4. CẬP NHẬT DỮ LIỆU (Insertion Phase) - Tự tính TWU trong Java
        int maxTID = config.lastTID;

        // --- BƯỚC 4A: TÍNH TỔNG TIỆN ÍCH CỦA TỪNG GIAO DỊCH (Transaction Utility - TU) ---
        // Map<TID, Tổng tiền đơn hàng>
        HashMap<Integer, Long> transactionUtilityMap = new HashMap<>();
        
        for (ITHUI_IO.TransactionTuple t : newData) {
            // Cộng dồn tiền của từng món vào tổng đơn hàng
            transactionUtilityMap.put(t.tid, transactionUtilityMap.getOrDefault(t.tid, 0L) + t.utility);
            
            // Tiện tay cập nhật maxTID luôn
            if (t.tid > maxTID) maxTID = t.tid;
        }
        config.lastTID = maxTID; // Cập nhật TID mới nhất để ghi lại file

        // --- BƯỚC 4B: CẬP NHẬT GLOBAL LIST & TÍNH TWU CHUẨN ---
        for (ITHUI_IO.TransactionTuple t : newData) {
            // 1. Mapping: Chuyển đổi ID chuỗi ("cb1") -> ID số nội bộ (0, 1...)
            if (!state.stringToIntMap.containsKey(t.itemStr)) {
                int newId = state.nextInternalId++;
                state.stringToIntMap.put(t.itemStr, newId);
                state.intToStringMap.put(newId, t.itemStr);
                state.globalLists.put(newId, new GlobalUtilityList(newId));
            }
            int internalId = state.stringToIntMap.get(t.itemStr);
            
            // 2. Lấy Global List của item đó ra
            GlobalUtilityList list = state.globalLists.get(internalId);
            
            // 3. Thêm thông tin giao dịch mới
            list.tids.add(t.tid);
            list.utilities.add(t.utility);
            
            // 4. Cập nhật SumIU (Tiện ích thực của item)
            // Ví dụ: Bán cái áo lãi 50k -> Cộng 50k
            list.sumIU += t.utility;
            
            // 5. Cập nhật TWU (Transaction Weighted Utility) - CHUẨN BÀI BÁO
            // TWU của item = Cộng dồn Tổng tiền của các đơn hàng mà nó xuất hiện
            // Ví dụ: Đơn hàng T1 tổng 500k, trong đó có cái áo. Thì TWU của áo += 500k (chứ không phải 50k)
            long currentTransactionTotal = transactionUtilityMap.get(t.tid);
            list.twu += currentTransactionTotal; 
        }

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
        System.out.println(">> Bắt đầu khai phá (Mining DFS) với ngưỡng: " + minUtil);
        
        // Bước 1: Lọc ra các item cấp 1 thỏa mãn điều kiện để làm "Hạt giống"
        List<GlobalUtilityList> seedLists = new ArrayList<>();
        for (GlobalUtilityList list : sortedLists) {
            // LỌC 1: TWU
            if (list.twu < minUtil) continue;
            // LỌC 2: Upper Bound
            if (list.sumIU + list.sumRU >= minUtil) {
                seedLists.add(list);
            }
        }

        // Bước 2: Gọi đệ quy (Parent ban đầu là NULL vì đang ở cấp 1)
        // Chúng ta truyền vào danh sách các "anh em" (siblings) để chúng tự bắt cặp với nhau
        runMiningDFS(null, seedLists, pq);

        // 9. LƯU TRẠNG THÁI & KẾT QUẢ
        state.topKPatterns = pq; // Lưu lại Top-k mới nhất
        saveState(state, binaryFile);
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
    // LƯU Ý QUAN TRỌNG: LIU-E và LIU-LB chỉ dùng để NÂNG NGƯỠNG (minUtil)
    // KHÔNG thêm pattern vào Top-K ở giai đoạn này!
    // Top-K chỉ được tạo trong giai đoạn Mining DFS
    static void runStrategy_LIU(List<GlobalUtilityList> sortedLists, PriorityQueue<Pattern> pq) {
        System.out.println(">> [LIU] Đang xây dựng ma trận...");

        // --- BƯỚC A: TẠO GIAO DỊCH TẠM ---
        // Mỗi giao dịch chứa danh sách [itemID, utility] đã sắp xếp theo TWU
        HashMap<Integer, ArrayList<int[]>> tempTransactions = new HashMap<>();
        HashMap<Integer, Integer> itemRankMap = new HashMap<>();
        for(int i=0; i<sortedLists.size(); i++) {
            itemRankMap.put(sortedLists.get(i).itemInternalId, i);
        }

        for (GlobalUtilityList list : sortedLists) {
            if (list.twu < minUtil) continue;
            for (int i = 0; i < list.tids.size(); i++) {
                int tid = list.tids.getInt(i);
                int util = list.utilities.getInt(i);
                tempTransactions.computeIfAbsent(tid, k -> new ArrayList<>())
                    .add(new int[]{list.itemInternalId, util});
            }
        }

        // --- BƯỚC B: XÂY DỰNG MA TRẬN LIU ---
        // Ma trận LIU lưu utility của các chuỗi liền kề (ordered & contiguous)
        // Key: "startItem_endItem", Value: tổng utility của chuỗi
        HashMap<String, Long> liuMatrix = new HashMap<>(); 
        HashMap<String, IntArrayList> liuMiddleItems = new HashMap<>();

        for (ArrayList<int[]> trans : tempTransactions.values()) {
            for (int i = 0; i < trans.size(); i++) {
                int[] startItem = trans.get(i);
                int u = startItem[0];
                long currentChainUtil = startItem[1]; 
                
                for (int j = i + 1; j < trans.size(); j++) {
                    int[] endItem = trans.get(j);
                    int[] prevItem = trans.get(j-1);
                    
                    // Chỉ xét chain LIÊN TIẾP (rank liền kề)
                    if (itemRankMap.get(endItem[0]) != itemRankMap.get(prevItem[0]) + 1) break; 
                    
                    currentChainUtil += endItem[1];
                    String key = u + "_" + endItem[0];
                    liuMatrix.put(key, liuMatrix.getOrDefault(key, 0L) + currentChainUtil); 
                    
                    // Lưu các item ở giữa (cho LIU-LB)
                    if (!liuMiddleItems.containsKey(key)) {
                        IntArrayList mids = new IntArrayList();
                        for(int k = i + 1; k < j; k++) mids.add(trans.get(k)[0]);
                        liuMiddleItems.put(key, mids);
                    }
                }
            }
        }

        // --- BƯỚC C.1: CHIẾN LƯỢC LIU-E ---
        // LIU-E: Dùng utility THỰC TẾ trong ma trận để nâng ngưỡng
        // CHỈ NÂNG NGƯỠNG, KHÔNG THÊM VÀO TOP-K
        System.out.println(">> [LIU-E] Đang chạy...");
        
        // Thu thập tất cả giá trị utility từ ma trận
        List<Long> allUtilities = new ArrayList<>(liuMatrix.values());
        
        // Sắp xếp giảm dần
        allUtilities.sort(Collections.reverseOrder());
        
        // Đưa K giá trị lớn nhất vào hàng đợi nâng ngưỡng
        // Hàng đợi nâng ngưỡng là MIN-HEAP, giữ K giá trị lớn nhất
        PriorityQueue<Long> thresholdQueue = new PriorityQueue<>();
        
        // Nếu đã có ngưỡng từ trước (warm-start), khởi tạo queue với giá trị đó
        if (minUtil > 0) {
            // Giữ các giá trị >= minUtil hiện tại
            for (Long util : allUtilities) {
                if (util >= minUtil) {
                    thresholdQueue.offer(util);
                    if (thresholdQueue.size() > K_VALUE) {
                        thresholdQueue.poll(); // Bỏ giá trị nhỏ nhất
                    }
                }
            }
        } else {
            // Khởi tạo từ đầu: lấy K giá trị lớn nhất
            for (Long util : allUtilities) {
                thresholdQueue.offer(util);
                if (thresholdQueue.size() > K_VALUE) {
                    thresholdQueue.poll();
                }
            }
        }
        
        // Cập nhật minUtil = giá trị nhỏ nhất trong K giá trị lớn nhất
        if (thresholdQueue.size() >= K_VALUE) {
            minUtil = Math.max(minUtil, thresholdQueue.peek());
        }
        
        System.out.println(">> [LIU-E] Ngưỡng sau LIU-E: " + minUtil);

        // --- BƯỚC C.2: CHIẾN LƯỢC LIU-LB ---
        // LIU-LB: Tính Lower Bound bằng cách bỏ bớt item ở giữa
        // CHỈ NÂNG NGƯỠNG, KHÔNG THÊM VÀO TOP-K
        System.out.println(">> [LIU-LB] Đang chạy...");
        
        for (Map.Entry<String, Long> entry : liuMatrix.entrySet()) {
            String key = entry.getKey();
            long chainUtility = entry.getValue(); 
            
            if (chainUtility < minUtil) continue; 

            IntArrayList mids = liuMiddleItems.get(key);
            if (mids != null && !mids.isEmpty()) {
                int mSize = mids.size();

                // Case 1: Bỏ 1 item ở giữa
                for (int m : mids) {
                    long lb = chainUtility - itemTotalUtilityMap.getOrDefault(m, 0L);
                    if (lb > minUtil) {
                        thresholdQueue.offer(lb);
                        if (thresholdQueue.size() > K_VALUE) {
                            thresholdQueue.poll();
                        }
                        if (thresholdQueue.size() >= K_VALUE) {
                            minUtil = Math.max(minUtil, thresholdQueue.peek());
                        }
                    }
                }

                // Case 2: Bỏ 2 item ở giữa
                if (mSize >= 2) {
                    for (int x = 0; x < mSize; x++) {
                        for (int y = x + 1; y < mSize; y++) {
                            long deduc = itemTotalUtilityMap.getOrDefault(mids.getInt(x), 0L) 
                                       + itemTotalUtilityMap.getOrDefault(mids.getInt(y), 0L);
                            long lb = chainUtility - deduc;
                            if (lb > minUtil) {
                                thresholdQueue.offer(lb);
                                if (thresholdQueue.size() > K_VALUE) {
                                    thresholdQueue.poll();
                                }
                                if (thresholdQueue.size() >= K_VALUE) {
                                    minUtil = Math.max(minUtil, thresholdQueue.peek());
                                }
                            }
                        }
                    }
                }

                // Case 3: Bỏ 3 item ở giữa
                if (mSize >= 3) {
                    for (int x = 0; x < mSize; x++) {
                        for (int y = x + 1; y < mSize; y++) {
                            for (int z = y + 1; z < mSize; z++) {
                                long deduc = itemTotalUtilityMap.getOrDefault(mids.getInt(x), 0L) 
                                           + itemTotalUtilityMap.getOrDefault(mids.getInt(y), 0L)
                                           + itemTotalUtilityMap.getOrDefault(mids.getInt(z), 0L);
                                long lb = chainUtility - deduc;
                                if (lb > minUtil) {
                                    thresholdQueue.offer(lb);
                                    if (thresholdQueue.size() > K_VALUE) {
                                        thresholdQueue.poll();
                                    }
                                    if (thresholdQueue.size() >= K_VALUE) {
                                        minUtil = Math.max(minUtil, thresholdQueue.peek());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        System.out.println(">> [LIU-LB] Ngưỡng sau LIU-LB: " + minUtil);
        System.out.println(">> [LIU] Hoàn tất. Ngưỡng cuối cùng: " + minUtil);
    }

    // ---------------------------------------------------------
    // CHIẾN LƯỢC 4: MINING (DFS Recursion)
    // ---------------------------------------------------------
    static void runMiningDFS(GlobalUtilityList parentList, List<GlobalUtilityList> siblings, PriorityQueue<Pattern> pq) {
        
        // Duyệt qua từng item làm "Gốc" (Prefix)
        for (int i = 0; i < siblings.size(); i++) {
            GlobalUtilityList prefixList = siblings.get(i);
            
            // Chuẩn bị danh sách con cho vòng đệ quy sau
            List<GlobalUtilityList> childLists = new ArrayList<>();
            
            // Duyệt các item đứng sau nó (Anh em - Extension)
            for (int j = i + 1; j < siblings.size(); j++) {
                GlobalUtilityList extensionList = siblings.get(j);
                
                // HỢP NHẤT 2 ANH EM: Prefix + Extension (Dựa trên Parent)
                // Công thức: U(Px) + U(Py) - U(P)
                GlobalUtilityList newList = constructUtilityList(prefixList, extensionList, parentList);
                
                // 1. RUC Strategy: Cập nhật Top-K ngay
                if (newList.sumIU >= minUtil) {
                    // Tái tạo lại Pattern ID (để lưu vào Queue)
                    IntArrayList newPatternItems = new IntArrayList();
                    // (Lưu ý: Trong thực tế để tối ưu RAM, ta không lưu full itemID trong list con 
                    // mà chỉ lưu ID của item cuối, khi nào in ra mới truy vết ngược lại.
                    // Nhưng ở đây để code dễ hiểu, tôi giả định list con kế thừa items của list cha).
                    // *Để đơn giản cho bản Java này: Tôi cộng gộp ID vào newList.tids (Hack nhẹ để lưu ID pattern)*
                    
                    // Logic tạo pattern để in ra (Không ảnh hưởng thuật toán)
                    if (parentList != null) newPatternItems.addAll(parentList.patternItems); // Code thêm: Cần field patternItems
                    else newPatternItems.add(prefixList.itemInternalId);
                    newPatternItems.add(extensionList.itemInternalId);
                    
                    updateTopK(pq, new Pattern(newPatternItems, newList.sumIU));
                }
                
                // 2. Pruning: Cắt tỉa Upper Bound
                if (newList.sumIU + newList.sumRU >= minUtil) {
                    childLists.add(newList);
                }
            }
            
            // Đệ quy sâu xuống nếu có con
            if (!childLists.isEmpty()) {
                // Lúc này prefixList trở thành Parent của đám childLists
                runMiningDFS(prefixList, childLists, pq);
            }
        }
    }
    // Hàm hợp nhất 2 Utility List (SIBLING MERGE)
    // Input: List X (Px), List Y (Py), List P (Parent)
    // Công thức: U(Pxy) = U(Px) + U(Py) - U(P)
    static GlobalUtilityList constructUtilityList(GlobalUtilityList listX, GlobalUtilityList listY, GlobalUtilityList listParent) {
        GlobalUtilityList res = new GlobalUtilityList(listY.itemInternalId); // ID của item đuôi
        long sumIU = 0;
        long sumRU = 0;
        
        // Thuật toán 3 Con trỏ (Nếu Parent != null) hoặc 2 Con trỏ (Nếu Parent == null - Cấp 1)
        int idxX = 0, idxY = 0, idxP = 0;
        
        while (idxX < listX.tids.size() && idxY < listY.tids.size()) {
            int tidX = listX.tids.getInt(idxX);
            int tidY = listY.tids.getInt(idxY);
            
            if (tidX == tidY) { // Tìm thấy giao dịch chung
                int commonTID = tidX;
                
                // 1. Lấy Utility của X và Y
                long uX = listX.utilities.getInt(idxX);
                long uY = listY.utilities.getInt(idxY);
                
                // 2. Lấy Utility của Parent (P) để trừ
                long uP = 0;
                if (listParent != null) {
                    // Tìm TID tương ứng trong Parent (Chắc chắn có vì P là cha của X, Y)
                    // Tối ưu: Dịch chuyển con trỏ P đến đúng vị trí (không cần search từ đầu)
                    while (listParent.tids.getInt(idxP) < commonTID) idxP++;
                    // Lúc này listParent.tids.getInt(idxP) == commonTID
                    uP = listParent.utilities.getInt(idxP);
                }
                
                // 3. Áp dụng công thức bài báo
                long newUtil = uX + uY - uP;
                
                // 4. RU là của thằng Y (đứng sau)
                long newRU = listY.remainingUtilities.getInt(idxY);
                
                // 5. Lưu kết quả
                res.tids.add(commonTID);
                res.utilities.add((int)newUtil);
                res.remainingUtilities.add((int)newRU);
                
                sumIU += newUtil;
                sumRU += newRU;
                
                idxX++;
                idxY++;
            } else if (tidX < tidY) {
                idxX++;
            } else {
                idxY++;
            }
        }
        res.sumIU = sumIU;
        res.sumRU = sumRU;
        // Kế thừa pattern items: Cha + Đuôi Y
        res.patternItems.addAll(listX.patternItems); // X chính là P + x (Đại diện cho nhánh trái)
        return res;
    }
    // Hàm cập nhật Top-K (RUC logic)
    static void updateTopK(PriorityQueue<Pattern> pq, Pattern p) {
        // 1. Kiểm tra điều kiện cơ bản
        if (p.utility < minUtil && pq.size() >= K_VALUE) return;
        
        // 2. [FIX] KHỬ TRÙNG: Kiểm tra xem Pattern này đã tồn tại trong hàng đợi chưa?
        // Duyệt qua hàng đợi (O(K) - rất nhanh vì K nhỏ)
        for (Pattern existing : pq) {
            // So sánh danh sách item (IntArrayList đã hỗ trợ hàm equals chuẩn)
            if (existing.items.equals(p.items)) {
                return; // Đã có rồi -> Bỏ qua, không thêm nữa
            }
        }
        
        // 3. Thêm vào hàng đợi
        pq.add(p);
        
        // 4. Duy trì kích thước K
        while (pq.size() > K_VALUE) {
            pq.poll(); // Đá thằng nhỏ nhất ra
        }
        
        // 5. Cập nhật minUtil
        if (pq.size() == K_VALUE) {
            minUtil = pq.peek().utility;
        }
    }
    // --- SERIALIZATION UTILS ---
    static void saveState(ITHUIState state, String filename) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(state);
            System.out.println(">> Đã lưu trạng thái Binary.");
        } catch(Exception e) { e.printStackTrace(); }
    }

    static ITHUIState loadState(String filename) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename))) {
            ITHUIState s = (ITHUIState) ois.readObject();
            // Reset RU sau khi load
            for(GlobalUtilityList l : s.globalLists.values()) l.resetRU();
            return s;
        } catch(Exception e) {
            return new ITHUIState(); // File chưa có thì tạo mới
        }
    }
}