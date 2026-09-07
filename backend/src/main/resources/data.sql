MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('OMS', '订单管理系统', 'http://localhost:8081', 'BEARER', 'admin', 'admin123', NULL, 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('WMS', '仓储管理系统', 'http://localhost:8083', 'BEARER', 'admin', 'admin123', NULL, 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('TMS', '运输管理系统', 'http://localhost:8082', 'NONE', NULL, NULL, NULL, 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('BMS', '计费系统', 'http://localhost:8084', 'API_KEY', NULL, NULL, NULL, 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('SRM', '供应商管理系统（预留）', NULL, 'NONE', NULL, NULL, NULL, 'MOCK', FALSE);

MERGE INTO ct_user (username, password, real_name, role, enabled)
KEY (username)
VALUES ('admin', 'pbkdf2$120000$BNgKdkFKHOJ2O7XtGWPowg==$oxGZBIUTlmp8VfmydZIW4Eo78kSQCXFzV8SSGI2Rv9w=', '系统管理员', 'ADMIN', TRUE);
MERGE INTO ct_user (username, password, real_name, role, enabled)
KEY (username)
VALUES ('planner', 'pbkdf2$120000$4UbYDRmMJYDzlXR4u87tzw==$+fzWyHrjFlMsqFdG3BxVXD6YgY42mm1SUD82RJ739Pg=', '计划员', 'PLANNER', TRUE);
MERGE INTO ct_user (username, password, real_name, role, enabled)
KEY (username)
VALUES ('viewer', 'pbkdf2$120000$cf1Uyo9Ui4NB9TuOHkkdPw==$xUZfwOiI6WOnuDjsTWveU1+IR+zDyFcC8JcPMS9k99A=', '只读用户', 'VIEWER', TRUE);

MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('OMS_STUCK', '订单审核卡单', 'ORDER_STUCK', '{"status":"AUDITED","hours":4}', 'HIGH', TRUE, 'OMS_HOLD');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('WMS_STUCK', '仓库拣货卡单', 'WMS_STUCK', '{"status":"PICKING","hours":6}', 'HIGH', TRUE, 'WMS_ALLOCATE');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('TMS_DELAY', '运输预计到达延误', 'TMS_DELAY', '{"hours":0}', 'HIGH', TRUE, 'TMS_SYNC_TRACK');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('LOW_STOCK', '库存低于安全库存', 'LOW_STOCK', '{}', 'MEDIUM', TRUE, 'SRM_PURCHASE_SUGGEST');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('COST_OVERRUN', '成本超预算', 'COST_OVERRUN', '{"days":7,"costPerOrderThreshold":0}', 'MEDIUM', TRUE, 'TMS_SWITCH_CARRIER');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('FORECAST_STOCKOUT', '预测即将缺货', 'FORECAST_STOCKOUT', '{"horizon":14,"serviceDays":3}', 'MEDIUM', TRUE, 'SRM_PURCHASE_SUGGEST');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('UNSHIPPED_ORDER', '订单未发货', 'ORDER_STUCK', '{"status":"PAID","hours":24}', 'LOW', TRUE, 'OMS_PRIORITIZE');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('EXCEPTION_SHIPMENT', '运输异常', 'TMS_DELAY', '{"exception":true}', 'HIGH', TRUE, 'TMS_SYNC_TRACK');

MERGE INTO ct_cost_target ("month", cost_type, target_amount)
KEY ("month", cost_type)
VALUES (FORMATDATETIME(CURRENT_DATE, 'yyyy-MM'), NULL, 100000.00);
