INSERT INTO default_products (position, sku, name, category, variant, size_oz, unit, cost_price, selling_price, reorder_level, qty_on_hand, active, image) VALUES
(0, 'COF-SPL-H08', 'Spanish Latte', 'Coffee', 'Hot', 8, 'cup', '45.00', '90.00', 10, 50, 1, ''),
(1, 'COF-SPL-H12', 'Spanish Latte', 'Coffee', 'Hot', 12, 'cup', '55.00', '110.00', 10, 50, 1, ''),
(2, 'COF-SPL-I16', 'Spanish Latte', 'Coffee', 'Iced', 16, 'cup', '60.00', '120.00', 10, 50, 1, ''),
(3, 'COF-SPL-I22', 'Spanish Latte', 'Coffee', 'Iced', 22, 'cup', '70.00', '140.00', 10, 50, 1, ''),
(4, 'COF-CRL-H08', 'Caramel Latte', 'Coffee', 'Hot', 8, 'cup', '45.00', '90.00', 10, 50, 1, ''),
(5, 'COF-CRL-H12', 'Caramel Latte', 'Coffee', 'Hot', 12, 'cup', '55.00', '110.00', 10, 50, 1, ''),
(6, 'COF-CRL-I16', 'Caramel Latte', 'Coffee', 'Iced', 16, 'cup', '60.00', '120.00', 10, 50, 1, ''),
(7, 'COF-CRL-I22', 'Caramel Latte', 'Coffee', 'Iced', 22, 'cup', '70.00', '140.00', 10, 50, 1, ''),
(8, 'COF-CFL-H08', 'Café Latte', 'Coffee', 'Hot', 8, 'cup', '45.00', '90.00', 10, 50, 1, ''),
(9, 'COF-CFL-H12', 'Café Latte', 'Coffee', 'Hot', 12, 'cup', '55.00', '110.00', 10, 50, 1, ''),
(10, 'COF-CFL-I16', 'Café Latte', 'Coffee', 'Iced', 16, 'cup', '60.00', '120.00', 10, 50, 1, ''),
(11, 'COF-CFL-I22', 'Café Latte', 'Coffee', 'Iced', 22, 'cup', '70.00', '140.00', 10, 50, 1, ''),
(12, 'COF-AMR-H08', 'Americano', 'Coffee', 'Hot', 8, 'cup', '45.00', '90.00', 10, 50, 1, ''),
(13, 'COF-AMR-H12', 'Americano', 'Coffee', 'Hot', 12, 'cup', '55.00', '110.00', 10, 50, 1, ''),
(14, 'COF-AMR-I16', 'Americano', 'Coffee', 'Iced', 16, 'cup', '60.00', '120.00', 10, 50, 1, ''),
(15, 'COF-AMR-I22', 'Americano', 'Coffee', 'Iced', 22, 'cup', '70.00', '140.00', 10, 50, 1, ''),
(16, 'FOD-CFR', 'Chicken Fillet with Rice', 'Food', '', 0, 'order', '70.00', '150.00', 10, 50, 1, ''),
(17, 'FOD-CAR', 'Carbonara', 'Food', '', 0, 'order', '65.00', '140.00', 10, 50, 1, ''),
(18, 'FOD-CSW', 'Chicken Sandwich', 'Food', '', 0, 'order', '50.00', '110.00', 10, 50, 1, ''),
(19, 'FOD-FRF', 'French Fries', 'Food', '', 0, 'order', '30.00', '80.00', 10, 50, 1, '');
INSERT INTO products (position, sku, name, category, variant, size_oz, unit, cost_price, selling_price, reorder_level, qty_on_hand, active, image)
SELECT position, sku, name, category, variant, size_oz, unit, cost_price, selling_price, reorder_level, qty_on_hand, active, image FROM default_products;
INSERT INTO users (position, username, salt, password_hash) VALUES
(0, 'admin', 'Y2MxMDQtcG9zLWRlbW8tc2FsdC0yMDI2', '5a4ff5d47489863fddafc852822d91eba2084950477b825d5fda37602063c5e8');
INSERT INTO settings (id, store_name, store_address, receipt_footer) VALUES
(1, 'Kofi', 'Your cafe address here', 'Thank you! Enjoy your coffee.');
