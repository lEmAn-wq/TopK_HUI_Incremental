import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.io.Serializable;
import java.util.*;

// -----------------------------------------------------------
// 1. GLOBAL UTILITY LIST (Giữ nguyên)
// -----------------------------------------------------------
class GlobalUtilityList implements Serializable {
    public int itemInternalId;
    public long sumIU;         
    public transient long sumRU;         
    public long twu;           
    public IntArrayList tids = new IntArrayList();
    public IntArrayList utilities = new IntArrayList();
    public transient IntArrayList remainingUtilities = new IntArrayList();
    public IntArrayList patternItems = new IntArrayList();

    public GlobalUtilityList(int id) {
        this.itemInternalId = id;
        this.patternItems.add(id);
    }
    public void resetRU() {
        this.remainingUtilities = new IntArrayList(tids.size());
        for(int i=0; i<tids.size(); i++) this.remainingUtilities.add(0);
    }
}

// -----------------------------------------------------------
// 2. PATTERN (Giữ nguyên)
// -----------------------------------------------------------
class Pattern implements Comparable<Pattern>, Serializable {
    public IntArrayList items;
    public long utility;

    public Pattern(IntArrayList items, long utility) {
        this.items = new IntArrayList(items);
        this.utility = utility;
    }
    @Override public int compareTo(Pattern o) {
        return Long.compare(this.utility, o.utility);
    }
    @Override public String toString() { return items.toString() + " : " + utility; }
}

// -----------------------------------------------------------
// 3. ITHUI STATE (Lưu trữ trạng thái)
// -----------------------------------------------------------
class ITHUIState implements Serializable {
    public HashMap<Integer, GlobalUtilityList> globalLists = new HashMap<>();
    public HashMap<String, Integer> stringToIntMap = new HashMap<>();
    public HashMap<Integer, String> intToStringMap = new HashMap<>();
    public int nextInternalId = 0;
    
    // Lưu ngưỡng MinUtil cuối cùng để Warm-Start cho lần sau
    public long lastMinUtil = 0; 
    
    public ITHUIState() {}
}

// ===========================================================
// 4. [PHASE 1 & 2] GLOBAL THRESHOLD QUEUE (Strict Top-k)
// Dùng để tìm ngưỡng minUtil nhanh nhất (chỉ chứa số)
// ===========================================================
class GlobalThresholdQueue {
    private int k;
    private PriorityQueue<Long> queue = new PriorityQueue<>(); // Min-Heap
    private long minUtil;

    public GlobalThresholdQueue(int k, long startThreshold) {
        this.k = k;
        this.minUtil = startThreshold;
    }

    public void add(long value) {
        if (value <= minUtil && queue.size() >= k) return;
        if (value < minUtil) return; // Nếu chưa đầy k nhưng nhỏ hơn ngưỡng khởi điểm thì cũng bỏ

        // Thêm vào hàng đợi
        queue.offer(value);

        // Duy trì kích thước cứng là K (Strict Top-k)
        while (queue.size() > k) {
            queue.poll(); // Loại bỏ phần tử nhỏ nhất
        }

        // Cập nhật minUtil
        if (queue.size() == k) {
            minUtil = queue.peek();
        }
    }

    public long getMinUtil() { return minUtil; }
    
    // Trả về các giá trị trong queue (dùng cho debug)
    public List<Long> getQueueValues() {
        List<Long> values = new ArrayList<>(queue);
        values.sort(Collections.reverseOrder());
        return values;
    }
}

// ===========================================================
// 5. [PHASE 3] TOP-K RESULT LIST (Dense Rank)
// Dùng để lưu kết quả khai phá, xử lý đồng hạng
// ===========================================================
class TopKResultList {
    private int k;
    private long minUtil;
    private List<Pattern> results = new ArrayList<>();

    public TopKResultList(int k, long startThreshold) {
        this.k = k;
        this.minUtil = startThreshold;
    }

    public void add(Pattern p) {
        if (p.utility < minUtil) return;

        results.add(p);
        // Sắp xếp giảm dần để tính Rank
        results.sort((p1, p2) -> Long.compare(p2.utility, p1.utility));

        // Logic Dense Rank (Top-k Mức giá trị)
        int distinctCount = 0;
        long lastVal = -1;
        long newThreshold = this.minUtil;
        boolean hasKLevels = false;

        for (Pattern pat : results) {
            if (pat.utility != lastVal) {
                distinctCount++;
                lastVal = pat.utility;
            }
            // Tìm thấy mức giá trị thứ k
            if (distinctCount == k) {
                newThreshold = pat.utility;
                hasKLevels = true;
                break;
            }
        }

        // Cắt đuôi nếu đã đủ K mức
        if (hasKLevels) {
            this.minUtil = newThreshold;
            // Chỉ loại bỏ các pattern NHỎ HƠN ngưỡng (giữ lại đồng hạng bằng ngưỡng)
            results.removeIf(pat -> pat.utility < minUtil);
        }
    }

    public long getMinUtil() { return minUtil; }
    public List<Pattern> getResults() { return results; }
    
    // Trả về số rank hiện có (số mức utility khác nhau)
    public int size() {
        Set<Long> distinctUtils = new HashSet<>();
        for (Pattern p : results) distinctUtils.add(p.utility);
        return distinctUtils.size();
    }
}