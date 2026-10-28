-- ScalarDB SQL version of schema.json

CREATE COORDINATOR TABLES IF NOT EXIST;

CREATE NAMESPACE IF NOT EXISTS customer;
CREATE NAMESPACE IF NOT EXISTS ordering;
CREATE NAMESPACE IF NOT EXISTS inventory;

CREATE TABLE IF NOT EXISTS customer.customers (
  customer_id INT PRIMARY KEY,
  name TEXT,
  credit_limit BIGINT,
  credit_total BIGINT
);
CREATE INDEX IF NOT EXISTS ON customer.customers (name);

CREATE TABLE IF NOT EXISTS ordering.orders (
  order_id TEXT PRIMARY KEY,
  customer_id INT,
  created_at BIGINT,
  status TEXT,
  total_amount BIGINT
);
CREATE INDEX IF NOT EXISTS ON ordering.orders (customer_id);
CREATE INDEX IF NOT EXISTS ON ordering.orders (created_at);

CREATE TABLE IF NOT EXISTS ordering.statements (
  order_id TEXT,
  line_no INT,
  item_id INT,
  qty INT,
  unit_price BIGINT,
  PRIMARY KEY (order_id, line_no)
);
CREATE INDEX IF NOT EXISTS ON ordering.statements (item_id);

CREATE TABLE IF NOT EXISTS inventory.items (
  item_id INT PRIMARY KEY,
  name TEXT,
  price BIGINT,
  stock INT
);
CREATE INDEX IF NOT EXISTS ON inventory.items (name);
