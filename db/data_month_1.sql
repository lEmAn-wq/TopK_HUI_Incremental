-- Tháng 10/2024 - Order ID 1 đến 20

USE [Tên_Database_Cua_Ban]; -- Nhớ thay tên DB
GO

-- 1. Insert Orders (20 đơn, tất cả đều Completed)
INSERT INTO [order_mgmt].[order] ([order_id], [order_date], [order_status], [actual_delivery_date]) VALUES
(1, '2024-10-01', 'Completed', '2024-10-02'), (2, '2024-10-02', 'Completed', '2024-10-03'),
(3, '2024-10-03', 'Completed', '2024-10-04'), (4, '2024-10-04', 'Completed', '2024-10-05'),
(5, '2024-10-05', 'Completed', '2024-10-06'), (6, '2024-10-06', 'Completed', '2024-10-07'),
(7, '2024-10-07', 'Completed', '2024-10-08'), (8, '2024-10-08', 'Completed', '2024-10-09'),
(9, '2024-10-09', 'Completed', '2024-10-10'), (10, '2024-10-10', 'Completed', '2024-10-11'),
(11, '2024-10-11', 'Completed', '2024-10-12'), (12, '2024-10-12', 'Completed', '2024-10-13'),
(13, '2024-10-13', 'Completed', '2024-10-14'), (14, '2024-10-14', 'Completed', '2024-10-15'),
(15, '2024-10-15', 'Completed', '2024-10-16'), (16, '2024-10-16', 'Completed', '2024-10-17'),
(17, '2024-10-17', 'Completed', '2024-10-18'), (18, '2024-10-18', 'Completed', '2024-10-19'),
(19, '2024-10-19', 'Completed', '2024-10-20'), (20, '2024-10-20', 'Completed', '2024-10-21');

-- CHIẾN THUẬT:
-- Item 105 (Laptop): Lợi nhuận cực cao (500k)
-- Combo 3 (Gear): Lợi nhuận cao (300k)
-- Cặp {105, cb3} sẽ xuất hiện chung 5 lần -> Tổng Utility cặp = (500+300)*5 = 4 Triệu -> Chắc chắn vào Top.

-- 2. Insert Single Items
INSERT INTO [order_mgmt].[order_single_item] ([order_id], [product_variant_id], [quantity], [unit_price], [unit_profit]) VALUES
(1, 105, 1, 5000000, 500000), -- Order 1: Có 105
(2, 101, 10, 10000, 2000),    -- Order 2: Đồ rẻ tiền
(3, 105, 1, 5000000, 500000), -- Order 3: Có 105
(5, 105, 1, 5000000, 500000), -- Order 5: Có 105
(7, 104, 2, 2000000, 200000),
(9, 105, 1, 5000000, 500000), -- Order 9: Có 105
(11, 105, 2, 5000000, 500000), -- Order 11: Có 105
(13, 104, 1, 2000000, 200000),
(15, 105, 1, 5000000, 500000), -- Order 15: Có 105
(17, 104, 1, 2000000, 200000),
(19, 101, 50, 10000, 2000);   -- Đồ rẻ số lượng lớn (lừa thuật toán)

-- 3. Insert Combo Items
INSERT INTO [order_mgmt].[order_combo_item] ([order_id], [combo_id], [quantity], [combo_price], [unit_profit]) VALUES
(1, 3, 1, 1000000, 300000),  -- Order 1: Có cb3 -> TẠO CẶP {105, cb3}
(3, 3, 1, 1000000, 300000),  -- Order 3: Có cb3 -> TẠO CẶP {105, cb3}
(5, 3, 1, 1000000, 300000),  -- Order 5: Có cb3 -> TẠO CẶP {105, cb3}
(9, 3, 1, 1000000, 300000),  -- Order 9: Có cb3 -> TẠO CẶP {105, cb3}
(11, 3, 1, 1000000, 300000), -- Order 11: Có cb3 -> TẠO CẶP {105, cb3}
(15, 3, 1, 1000000, 300000), -- Order 15: Có cb3 -> TẠO CẶP {105, cb3}
(2, 1, 5, 200000, 50000),
(4, 2, 1, 150000, 30000),
(13, 2, 2, 150000, 30000);
GO