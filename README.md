# Improved ITHUI with Global Threshold & Dense Rank Mining

Dự án này triển khai thuật toán **ITHUI Cải tiến** (Incremental Top-k High Utility Itemset Mining), tối ưu hóa cho bài toán khai phá dữ liệu thương mại điện tử với đặc thù "Dense Rank" (Xếp hạng dày - Top-k mức giá trị).

## ĐỊNH DANH THUẬT TOÁN
* **Tên gọi:** Improved ITHUI Algorithm.
* **Cơ chế:** Incremental (Tăng trưởng) - Xử lý dữ liệu mới mà không cần quét lại dữ liệu cũ.
* **Chiến lược Nâng ngưỡng:** Global Threshold Strategy (từ FTKHUIM) kết hợp Recursive LIU-LB.
* **Cơ chế Xếp hạng:** Dense Rank (Top-k Mức giá trị, giữ lại toàn bộ item đồng hạng).

---

## CẤU TRÚC CODE & LOGIC

### 1. `ITHUI_Structures.java`
* `GlobalThresholdQueue`: Hàng đợi ưu tiên (chỉ chứa số) dùng cho Phase 1. Tuân thủ Strict Top-k (kích thước cố định K) để tìm ngưỡng cắt tỉa nhanh nhất.
* `TopKResultList`: Danh sách kết quả dùng cho Phase 2. Tuân thủ Dense Rank (kích thước linh hoạt) để giữ lại tất cả các pattern đồng hạng.
* `GlobalUtilityList`: Lưu trữ thông tin item, hỗ trợ tính toán tăng trưởng.

### 2. `ITHUI_Algorithm.java` (Quy trình chạy)
1.  **Warm-Start:** Khôi phục `lastMinUtil` từ file `.bin` làm ngưỡng khởi điểm.
2.  **Insertion:** Lấy dữ liệu mới từ SQL, cập nhật vào hệ thống List.
3.  **Phase 1 (Nâng Ngưỡng):**
    * Dùng `GlobalThresholdQueue`.
    * Chạy **RIU** (duyệt Item) $\to$ **Restructuring** (tính RU) $\to$ **LIU-E** (duyệt Ma trận) $\to$ **LIU-LB** (Đệ quy cắt tỉa sâu).
    * Kết quả: Thu được con số `minUtil` tối ưu.
4.  **Phase 2 (Khai Phá):**
    * Dùng `TopKResultList` với vốn là `minUtil` từ Phase 1.
    * Chạy thuật toán **DFS Mining**.
    * Lưu kết quả theo dạng Dense Rank (Top 1 có thể có 50 itemset nếu bằng giá trị).
5.  **Lưu trữ:** Ghi kết quả vào `output.txt` (chèn lên đầu, giữ lịch sử) và lưu trạng thái binary.

---

## HƯỚNG DẪN SỬ DỤNG

### Cài đặt
1.  Import project vào IDE (IntelliJ/Eclipse) dưới dạng Maven Project.
2.  Cấu hình Database trong file `.env`.
3.  Kiểm tra `output.txt` để đặt tham số `k`.

### Chạy Demo
1.  Chạy `InsertData.java` để nạp dữ liệu mẫu (Tháng 1, 2...).
2.  Chạy `ITHUI_Algorithm.java`.
3.  Xem kết quả tại `output.txt`. File này sẽ lưu lại lịch sử các lần chạy trước đó để bạn so sánh sự thay đổi của Top-k theo thời gian.