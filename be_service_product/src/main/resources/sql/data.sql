-- Insert categories
INSERT INTO tb_category (cate_code, cate_name, cate_des, create_time, update_time)
VALUES ('ELECTRONICS', 'Điện tử', 'Các sản phẩm điện tử, công nghệ', NOW(), NOW()),
       ('FASHION', 'Thời trang', 'Quần áo, phụ kiện thời trang', NOW(), NOW()),
       ('HOME_APPLIANCES', 'Gia dụng', 'Đồ gia dụng, thiết bị nhà bếp', NOW(), NOW()),
       ('FOOTWEAR', 'Giày dép', 'Các loại giày dép nam nữ', NOW(), NOW()),
       ('FOOD', 'Thực phẩm', 'Đồ ăn, thức uống, thực phẩm tươi sống', NOW(), NOW());

-- Electronics (category_id = 1)
INSERT INTO tb_product (pro_name, pro_price, pro_stock, pro_des, pro_img, category_id, create_time, update_time)
VALUES ('iPhone 15 Pro Max', 30000000, 100, 'Điện thoại cao cấp Apple', 'iphone15.jpg', 1, NOW(), NOW()),
       ('Samsung Galaxy S24 Ultra', 31000000, 80, 'Điện thoại cao cấp Samsung', 's24ultra.jpg', 1, NOW(), NOW()),
       ('MacBook Pro M3', 45000000, 50, 'Laptop Apple chip M3', 'macbookm3.jpg', 1, NOW(), NOW()),
       ('Dell XPS 15', 40000000, 40, 'Laptop Dell cao cấp', 'dellxps.jpg', 1, NOW(), NOW()),
       ('AirPods Pro 2', 6000000, 200, 'Tai nghe không dây Apple', 'airpodspro.jpg', 1, NOW(), NOW()),
       ('Sony WH-1000XM5', 7500000, 150, 'Tai nghe chống ồn Sony', 'sonywh.jpg', 1, NOW(), NOW()),
       ('Apple Watch Series 9', 10000000, 120, 'Đồng hồ thông minh Apple', 'applewatch9.jpg', 1, NOW(), NOW()),
       ('Galaxy Watch 6', 7000000, 100, 'Đồng hồ thông minh Samsung', 'galaxywatch6.jpg', 1, NOW(), NOW()),
       ('iPad Pro M2', 22000000, 60, 'Máy tính bảng Apple', 'ipadpro.jpg', 1, NOW(), NOW()),
       ('Samsung Galaxy Tab S9', 20000000, 70, 'Máy tính bảng Samsung', 'tabs9.jpg', 1, NOW(), NOW());

-- Fashion (category_id = 2)
INSERT INTO tb_product (pro_name, pro_price, pro_stock, pro_des, pro_img, category_id, create_time, update_time)
VALUES ('Áo thun nam cotton', 150000, 500, 'Áo thun 100% cotton thoáng mát', 'aothunnam.jpg', 2, NOW(), NOW()),
       ('Quần jean nam ống suông', 350000, 300, 'Quần jean nam cao cấp', 'quanjean.jpg', 2, NOW(), NOW()),
       ('Áo sơ mi công sở', 250000, 200, 'Áo sơ mi nam ngắn tay', 'aosomi.jpg', 2, NOW(), NOW()),
       ('Váy nữ dáng xòe', 400000, 150, 'Váy công sở thanh lịch', 'vaynu.jpg', 2, NOW(), NOW()),
       ('Áo khoác gió thể thao', 300000, 250, 'Áo khoác gió nhẹ, chống nước', 'aokhoacgio.jpg', 2, NOW(), NOW()),
       ('Quần tây nữ công sở', 280000, 180, 'Quần tây co giãn thoải mái', 'quantaynu.jpg', 2, NOW(), NOW()),
       ('Đầm dạ hội cao cấp', 1200000, 50, 'Đầm dự tiệc sang trọng', 'damdahoi.jpg', 2, NOW(), NOW()),
       ('Áo len nữ mùa đông', 250000, 300, 'Áo len giữ ấm phong cách Hàn Quốc', 'aolennu.jpg', 2, NOW(), NOW()),
       ('Quần short kaki nam', 180000, 400, 'Quần short mặc nhà, đi chơi', 'quanshortnam.jpg', 2, NOW(), NOW()),
       ('Đồ bộ ngủ nữ', 200000, 350, 'Bộ đồ ngủ lụa cao cấp', 'dobonu.jpg', 2, NOW(), NOW());

-- Home Appliances (category_id = 3)
INSERT INTO tb_product (pro_name, pro_price, pro_stock, pro_des, pro_img, category_id, create_time, update_time)
VALUES ('Nồi chiên không dầu Philips', 2500000, 100, 'Nồi chiên không dầu dung tích lớn', 'noichienphilips.jpg', 3,
        NOW(), NOW()),
       ('Lò vi sóng Sharp', 1800000, 80, 'Lò vi sóng đa chức năng', 'lovisong.jpg', 3, NOW(), NOW()),
       ('Máy xay sinh tố Panasonic', 1200000, 150, 'Máy xay công suất cao', 'mayxay.jpg', 3, NOW(), NOW()),
       ('Nồi cơm điện Cuckoo', 3500000, 60, 'Nồi cơm điện tử cao cấp', 'noicomdien.jpg', 3, NOW(), NOW()),
       ('Bếp từ Sunhouse', 800000, 200, 'Bếp từ đơn gọn nhẹ', 'beptu.jpg', 3, NOW(), NOW()),
       ('Máy ép trái cây chậm', 2200000, 70, 'Máy ép chậm giữ nguyên dưỡng chất', 'mayepcham.jpg', 3, NOW(), NOW()),
       ('Quạt cây Mitsubishi', 1500000, 120, 'Quạt đứng động cơ bền bỉ', 'quatcay.jpg', 3, NOW(), NOW()),
       ('Máy hút bụi Dyson', 15000000, 30, 'Máy hút bụi cầm tay không dây', 'dyson.jpg', 3, NOW(), NOW()),
       ('Bàn là hơi nước Philips', 900000, 100, 'Bàn là hơi nước tiện lợi', 'banla.jpg', 3, NOW(), NOW()),
       ('Máy lọc không khí Xiaomi', 3000000, 80, 'Máy lọc không khí gia đình', 'maylockhongkhi.jpg', 3, NOW(), NOW());

-- Footwear (category_id = 4)
INSERT INTO tb_product (pro_name, pro_price, pro_stock, pro_des, pro_img, category_id, create_time, update_time)
VALUES ('Nike Air Force 1', 2500000, 150, 'Giày thể thao nam nữ', 'nikeaf1.jpg', 4, NOW(), NOW()),
       ('Adidas Ultraboost', 3500000, 100, 'Giày chạy bộ chuyên nghiệp', 'ultraboost.jpg', 4, NOW(), NOW()),
       ('Giày lười da bò nam', 1200000, 80, 'Giày công sở nam', 'giayluoi.jpg', 4, NOW(), NOW()),
       ('Dép quai ngang nam', 150000, 500, 'Dép mang trong nhà, đi biển', 'depquaingang.jpg', 4, NOW(), NOW()),
       ('Giày cao gót nữ 7cm', 450000, 200, 'Giày cao gót công sở', 'giaycaogot.jpg', 4, NOW(), NOW()),
       ('Sandal nữ đính đá', 350000, 180, 'Sandal thời trang nữ', 'sandalnu.jpg', 4, NOW(), NOW()),
       ('Giày búp bê nữ', 250000, 300, 'Giày búp bê đế mềm', 'giaybupbe.jpg', 4, NOW(), NOW()),
       ('Dép sục Crocs', 800000, 250, 'Dép Crocs siêu nhẹ', 'crocs.jpg', 4, NOW(), NOW()),
       ('Giày boots da nữ', 850000, 90, 'Giày boots mùa đông', 'bootsnu.jpg', 4, NOW(), NOW()),
       ('Giày Oxford nam', 1500000, 70, 'Giày da nam buộc dây', 'oxford.jpg', 4, NOW(), NOW());

-- Food (category_id = 5)
INSERT INTO tb_product (pro_name, pro_price, pro_stock, pro_des, pro_img, category_id, create_time, update_time)
VALUES ('Thịt bò Wagyu A5 (500g)', 1500000, 30, 'Thịt bò nhập khẩu Nhật Bản', 'wagyu.jpg', 5, NOW(), NOW()),
       ('Gạo ST25 (5kg)', 180000, 500, 'Gạo ngon nhất thế giới', 'gaost25.jpg', 5, NOW(), NOW()),
       ('Sữa tươi TH True Milk (1 thùng)', 350000, 200, 'Sữa tươi sạch nguyên chất', 'thtruemilk.jpg', 5, NOW(), NOW()),
       ('Cherry nhập khẩu Úc', 500000, 80, 'Cherry tươi ngon', 'cherry.jpg', 5, NOW(), NOW()),
       ('Mật ong rừng nguyên chất (1L)', 800000, 50, 'Mật ong rừng Tây Nguyên', 'matong.jpg', 5, NOW(), NOW()),
       ('Cá hồi Nauy fillet (1kg)', 650000, 60, 'Cá hồi tươi nhập khẩu', 'cahoi.jpg', 5, NOW(), NOW()),
       ('Bánh quy bơ Danisa', 200000, 300, 'Bánh quy bơ cao cấp', 'danisa.jpg', 5, NOW(), NOW()),
       ('Nước ép cam nguyên chất (1L)', 80000, 150, 'Nước ép cam tự nhiên', 'nuocepcam.jpg', 5, NOW(), NOW()),
       ('Hạt điều rang muối (500g)', 150000, 250, 'Hạt điều Bình Phước', 'hatdieu.jpg', 5, NOW(), NOW()),
       ('Cà phê hạt nguyên chất (500g)', 120000, 400, 'Cà phê Robusta rang mộc', 'caphe.jpg', 5, NOW(), NOW());
