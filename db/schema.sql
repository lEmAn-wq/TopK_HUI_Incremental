IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'order_mgmt') EXEC('CREATE SCHEMA [order_mgmt]')
GO

-- ************************************** [order_mgmt].[order]
-- Tham chiếu: user, shipping_promotions
CREATE TABLE [order_mgmt].[order]
(
 [order_id]                 int NOT NULL ,
 [order_date]               datetime NOT NULL ,
 [order_status]             nvarchar(50) NOT NULL ,
 [actual_delivery_date]     datetime NULL ,

 CONSTRAINT [PK_1_order] PRIMARY KEY CLUSTERED ([order_id] ASC),
);
GO

-- ************************************** [order_mgmt].[order_combo_item]
-- Tham chiếu: order, product_combo
CREATE TABLE [order_mgmt].[order_combo_item]
(
 [order_id]    int NOT NULL ,
 [combo_id]    int NOT NULL ,
 [quantity]    smallint NOT NULL ,
 [combo_price] money NOT NULL ,
 [unit_profit] money NOT NULL ,


 CONSTRAINT [PK_1_order_combo] PRIMARY KEY CLUSTERED ([order_id] ASC, [combo_id] ASC),
 CONSTRAINT [FK_1_order_combo] FOREIGN KEY ([order_id])  REFERENCES [order_mgmt].[order]([order_id]),
);
GO


-- ************************************** [order_mgmt].[order_single_item]
-- Tham chiếu: order, product_variant
CREATE TABLE [order_mgmt].[order_single_item]
(
 [order_id]           int NOT NULL ,
 [product_variant_id] int NOT NULL ,
 [quantity]           smallint NOT NULL ,
 [unit_price]         money NOT NULL ,
 [unit_profit]        money NOT NULL ,

 CONSTRAINT [PK_1_order_single] PRIMARY KEY CLUSTERED ([order_id] ASC, [product_variant_id] ASC),
 CONSTRAINT [FK_1_order_single] FOREIGN KEY ([order_id])  REFERENCES [order_mgmt].[order]([order_id]),
);
GO


