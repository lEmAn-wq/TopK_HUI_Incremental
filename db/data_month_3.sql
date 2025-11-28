-- Tháng 12/2024 - Order ID 41 đến 60

USE [Tên_Database_Cua_Ban];
GO

INSERT INTO [order_mgmt].[order] ([order_id], [order_date], [order_status], [actual_delivery_date]) VALUES
(41, '2024-12-01', 'Completed', '2024-12-03'), (42, '2024-12-02', 'Completed', '2024-12-04'),
(43, '2024-12-03', 'Completed', '2024-12-05'), (44, '2024-12-04', 'Completed', '2024-12-06'),
(45, '2024-12-05', 'Completed', '2024-12-07'), (46, '2024-12-06', 'Completed', '2024-12-08'),
(47, '2024-12-07', 'Completed', '2024-12-09'), (48, '2024-12-08', 'Completed', '2024-12-10'),
(49, '2024-12-09', 'Completed', '2024-12-11'), (50, '2024-12-10', 'Completed', '2024-12-12'),
(51, '2024-12-11', 'Completed', '2024-12-13'), (52, '2024-12-12', 'Completed', '2024-12-14'),
(53, '2024-12-13', 'Completed', '2024-12-15'), (54, '2024-12-14', 'Completed', '2024-12-16'),
(55, '2024-12-15', 'Completed', '2024-12-17'), (56, '2024-12-16', 'Completed', '2024-12-18'),
(57, '2024-12-17', 'Completed', '2024-12-19'), (58, '2024-12-18', 'Completed', '2024-12-20'),
(59, '2024-12-19', 'Completed', '2024-12-21'), (60, '2024-12-20', 'Completed', '2024-12-22');

-- CHIẾN THUẬT GIAI ĐOẠN 3:
-- Tạo Pattern 3 item: {104, 105, cb3} (Mua Full Set: Phụ kiện + Laptop + Gear)
-- Xuất hiện 4 lần. Tổng lợi nhuận cực khủng: (200k + 500k + 300k) * 4 = 4 Triệu.

INSERT INTO [order_mgmt].[order_single_item] ([order_id], [product_variant_id], [quantity], [unit_price], [unit_profit]) VALUES
(41, 104, 1, 2000000, 200000), (41, 105, 1, 5000000, 500000), -- Đi với cb3 ở dưới
(43, 104, 1, 2000000, 200000), (43, 105, 1, 5000000, 500000), -- Đi với cb3 ở dưới
(45, 104, 1, 2000000, 200000), (45, 105, 1, 5000000, 500000), -- Đi với cb3 ở dưới
(47, 104, 1, 2000000, 200000), (47, 105, 1, 5000000, 500000), -- Đi với cb3 ở dưới
(50, 101, 100, 10000, 2000);

INSERT INTO [order_mgmt].[order_combo_item] ([order_id], [combo_id], [quantity], [combo_price], [unit_profit]) VALUES
(41, 3, 1, 1000000, 300000),
(43, 3, 1, 1000000, 300000),
(45, 3, 1, 1000000, 300000),
(47, 3, 1, 1000000, 300000),
(55, 1, 1, 200000, 50000);
GO