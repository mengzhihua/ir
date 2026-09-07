CREATE TABLE IF NOT EXISTS ct_system (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    base_url VARCHAR(255),
    auth_type VARCHAR(32) NOT NULL,
    username VARCHAR(100),
    password VARCHAR(255),
    api_key VARCHAR(255),
    mode VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    last_health_at TIMESTAMP,
    last_health_ok BOOLEAN,
    last_error VARCHAR(500),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ct_sync_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_code VARCHAR(32) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    rows INT,
    message VARCHAR(500),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ct_order_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(80) NOT NULL UNIQUE,
    channel_code VARCHAR(32),
    shop_code VARCHAR(64),
    warehouse_code VARCHAR(32),
    province VARCHAR(64),
    city VARCHAR(64),
    status VARCHAR(32),
    pay_amount DECIMAL(18,2),
    freight DECIMAL(18,2),
    qty DECIMAL(18,2),
    order_time TIMESTAMP,
    pay_time TIMESTAMP,
    ship_time TIMESTAMP,
    complete_time TIMESTAMP,
    carrier_code VARCHAR(32),
    tracking_no VARCHAR(80),
    wms_order_no VARCHAR(80),
    tms_order_no VARCHAR(80),
    synced_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ct_wms_order_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    external_no VARCHAR(80),
    warehouse_code VARCHAR(32),
    status VARCHAR(32),
    total_qty DECIMAL(18,2),
    picked_qty DECIMAL(18,2),
    shipped_qty DECIMAL(18,2),
    carrier VARCHAR(32),
    tracking_no VARCHAR(80),
    packed_at TIMESTAMP,
    shipped_at TIMESTAMP,
    synced_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ct_shipment_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    waybill_code VARCHAR(80) NOT NULL UNIQUE,
    source_no VARCHAR(80),
    carrier_code VARCHAR(32),
    status VARCHAR(32),
    from_site_code VARCHAR(32),
    planned_arrive_time TIMESTAMP,
    actual_arrive_time TIMESTAMP,
    freight_amount DECIMAL(18,2),
    exception_flag BOOLEAN,
    synced_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ct_inventory_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_system VARCHAR(32) NOT NULL,
    warehouse_code VARCHAR(32) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    qty_on_hand DECIMAL(18,2),
    qty_reserved DECIMAL(18,2),
    qty_available DECIMAL(18,2),
    safety_qty DECIMAL(18,2),
    synced_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE(source_system, warehouse_code, sku)
);

CREATE TABLE IF NOT EXISTS ct_sales_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sales_date DATE NOT NULL,
    sku VARCHAR(64) NOT NULL,
    warehouse_code VARCHAR(32) NOT NULL,
    channel_code VARCHAR(32) NOT NULL,
    qty DECIMAL(18,2),
    amount DECIMAL(18,2),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE(sales_date, sku, warehouse_code, channel_code)
);

CREATE TABLE IF NOT EXISTS ct_cost_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_date DATE NOT NULL,
    order_no VARCHAR(80),
    warehouse_code VARCHAR(32),
    carrier_code VARCHAR(32),
    cost_type VARCHAR(32) NOT NULL,
    amount DECIMAL(18,2),
    source_system VARCHAR(32),
    remark VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ct_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(32) NOT NULL,
    params VARCHAR(2000),
    severity VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL,
    suggested_action VARCHAR(64),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ct_alert (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_no VARCHAR(80) NOT NULL UNIQUE,
    rule_code VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_key VARCHAR(100) NOT NULL,
    warehouse_code VARCHAR(32),
    title VARCHAR(255),
    detail VARCHAR(2000),
    status VARCHAR(16) NOT NULL,
    suggested_action VARCHAR(64),
    action_id BIGINT,
    created_at TIMESTAMP,
    resolved_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ct_action (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action_no VARCHAR(80) NOT NULL UNIQUE,
    type VARCHAR(64) NOT NULL,
    target_system VARCHAR(32),
    target_key VARCHAR(100),
    params VARCHAR(4000),
    status VARCHAR(16) NOT NULL,
    result VARCHAR(2000),
    alert_id BIGINT,
    operator VARCHAR(100),
    expected_saving DECIMAL(18,2),
    created_at TIMESTAMP,
    executed_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ct_forecast (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_no VARCHAR(80) NOT NULL UNIQUE,
    sku VARCHAR(64) NOT NULL,
    warehouse_code VARCHAR(32),
    method VARCHAR(32) NOT NULL,
    horizon INT NOT NULL,
    mape DECIMAL(18,6),
    result_json CLOB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ct_scenario (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scenario_no VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    baseline BOOLEAN NOT NULL,
    params_json CLOB,
    result_json CLOB,
    status VARCHAR(16) NOT NULL,
    total_cost DECIMAL(18,2),
    service_level DECIMAL(18,6),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ct_cost_target (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    "month" VARCHAR(7) NOT NULL,
    cost_type VARCHAR(32),
    target_amount DECIMAL(18,2),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE("month", cost_type)
);

CREATE TABLE IF NOT EXISTS ct_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(100),
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ct_op_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator VARCHAR(100),
    module VARCHAR(100),
    action VARCHAR(100),
    target VARCHAR(255),
    detail VARCHAR(2000),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
