import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.io.Serializable;
import java.util.HashMap;
import java.util.PriorityQueue;

// -----------------------------------------------------------
// 1. GLOBAL UTILITY LIST: Cấu trúc lõi lưu trữ Item
// -----------------------------------------------------------
class GlobalUtilityList implements Serializable {
    public int itemInternalId; // ID nội bộ (0, 1, 2...)
    public long sumIU;         // Tổng tiện ích thực
    public transient long sumRU;         // Tổng tiện ích còn lại
    public long twu;           // Transaction Weighted Utility

    // Dùng FastUtil IntArrayList để tiết kiệm RAM (như mảng int[] trong C)
    public IntArrayList tids;
    public IntArrayList utilities;

    // TRANSIENT: Không lưu cột này xuống ổ cứng!
    // Khi load lên nó sẽ null, ta sẽ reset về 0.
    public transient IntArrayList remainingUtilities; 

    // Thêm biến này để lưu vết Pattern ID (phục vụ in kết quả)
    public IntArrayList patternItems = new IntArrayList();

    public GlobalUtilityList() {} // Cho Kryo
    public GlobalUtilityList(int id) {
        this.itemInternalId = id;
        this.patternItems.add(id); // Mặc định chứa chính nó
        this.tids = new IntArrayList();
        this.utilities = new IntArrayList();
        this.remainingUtilities = new IntArrayList();
    }

    // Hàm reset RU về 0 (như malloc lại mảng 0)
    public void resetRU() {
        this.remainingUtilities = new IntArrayList(tids.size());
        for(int i=0; i<tids.size(); i++) this.remainingUtilities.add(0);
    }
}

// -----------------------------------------------------------
// 2. PATTERN: Đối tượng để lưu kết quả Top-k
// -----------------------------------------------------------
class Pattern implements Comparable<Pattern>, Serializable {
    public IntArrayList items; // Danh sách item trong pattern
    public long utility;       // Lợi nhuận

    public Pattern(IntArrayList items, long utility) {
        this.items = new IntArrayList(items);
        this.utility = utility;
    }

    // So sánh để PriorityQueue biết ai lớn ai nhỏ
    @Override
    public int compareTo(Pattern o) {
        // Sắp xếp tăng dần theo Utility (để thằng nhỏ nhất nằm đầu Queue - dễ đá ra)
        return Long.compare(this.utility, o.utility);
    }
    
    @Override
    public String toString() {
        return "Pattern: " + items.toString() + " - Utility: " + utility;
    }
}

// -----------------------------------------------------------
// 3. ITHUI STATE: Trạng thái toàn cục (Sẽ lưu ra file .bin)
// -----------------------------------------------------------
class ITHUIState implements Serializable {
    // Map lưu trữ: Item ID (nội bộ) -> Global List
    public HashMap<Integer, GlobalUtilityList> globalLists = new HashMap<>();
    
    // Từ điển Mapping: "cb1" -> 1001, "105" -> 5
    public HashMap<String, Integer> stringToIntMap = new HashMap<>();
    public HashMap<Integer, String> intToStringMap = new HashMap<>();
    
    // Bộ đếm để cấp phát ID nội bộ mới
    public int nextInternalId = 0;

    // Top-k cũ để làm "mồi" nâng ngưỡng lần sau
    public PriorityQueue<Pattern> topKPatterns = new PriorityQueue<>();
    
    public ITHUIState() {}
}