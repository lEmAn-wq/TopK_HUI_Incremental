-- Tháng 11/2024 - Order ID 21 đến 40

USE [Tên_Database_Cua_Ban];
GO

-- 1. Insert Orders
INSERT INTO [order_mgmt].[order] ([order_id], [order_date], [order_status], [actual_delivery_date]) VALUES
(21, '2024-11-01', 'Completed', '2024-11-02'), (22, '2024-11-02', 'Completed', '2024-11-03'),
(23, '2024-11-03', 'Completed', '2024-11-04'), (24, '2024-11-04', 'Completed', '2024-11-05'),
(25, '2024-11-05', 'Completed', '2024-11-06'), (26, '2024-11-06', 'Completed', '2024-11-07'),
(27, '2024-11-07', 'Completed', '2024-11-08'), (28, '2024-11-08', 'Completed', '2024-11-09'),
(29, '2024-11-09', 'Completed', '2024-11-10'), (30, '2024-11-10', 'Completed', '2024-11-11'),
(31, '2024-11-11', 'Completed', '2024-11-12'), (32, '2024-11-12', 'Completed', '2024-11-13'),
(33, '2024-11-13', 'Completed', '2024-11-14'), (34, '2024-11-14', 'Completed', '2024-11-15'),
(35, '2024-11-15', 'Completed', '2024-11-16'), (36, '2024-11-16', 'Completed', '2024-11-17'),
(37, '2024-11-17', 'Completed', '2024-11-18'), (38, '2024-11-18', 'Cancelled', NULL), -- Đơn này bị Hủy (để test lọc)
(39, '2024-11-19', 'Processing', NULL), -- Đơn này chưa giao (để test lọc)
(40, '2024-11-20', 'Completed', '2024-11-21');

-- CHIẾN THUẬT GIAI ĐOẠN 2:
-- Tạo thêm Pattern mới: {104, 105} (Mua phụ kiện + Laptop)
-- Tiếp tục duy trì Pattern cũ {105, cb3}

INSERT INTO [order_mgmt].[order_single_item] ([order_id], [product_variant_id], [quantity], [unit_price], [unit_profit]) VALUES
(21, 104, 1, 2000000, 200000), (21, 105, 1, 5000000, 500000), -- Cặp {104, 105}
(23, 104, 1, 2000000, 200000), (23, 105, 1, 5000000, 500000), -- Cặp {104, 105}
(25, 104, 1, 2000000, 200000), (25, 105, 1, 5000000, 500000), -- Cặp {104, 105}
(27, 105, 1, 5000000, 500000), -- 105 đi lẻ
(38, 105, 10, 5000000, 500000), -- Đơn hàng cực lớn nhưng bị HỦY (Ko được tính)
(40, 105, 1, 5000000, 500000); -- 105 đi với cb3 tiếp

INSERT INTO [order_mgmt].[order_combo_item] ([order_id], [combo_id], [quantity], [combo_price], [unit_profit]) VALUES
(27, 3, 1, 1000000, 300000), -- 27: Cặp {105, cb3}
(40, 3, 1, 1000000, 300000), -- 40: Cặp {105, cb3}
(22, 1, 1, 200000, 50000),
(24, 1, 1, 200000, 50000);
GO