MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('OMS', '订单管理系统', 'http://localhost:8081', 'BEARER', 'admin', 'admin123', 'oms-open-key', 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('WMS', '仓储管理系统', 'http://localhost:8083', 'BEARER', 'admin', 'admin123', 'wms-open-key', 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('TMS', '运输管理系统', 'http://localhost:8082', 'API_KEY', NULL, NULL, 'tms-open-key', 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('BMS', '计费系统', 'http://localhost:8084', 'API_KEY', NULL, NULL, 'bms-open-key', 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('SRM', '供应商管理系统', 'http://localhost:8087', 'API_KEY', 'admin', 'admin123', 'srm-wms-key', 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('SAP', 'ERP 系统', 'http://localhost:8085', 'API_KEY', 'admin', 'admin123', 'sap-open-key', 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('OA', '协同办公', 'http://localhost:8086', 'API_KEY', NULL, NULL, 'oa-open-key', 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('BOM', '产品结构', 'http://localhost:8088', 'API_KEY', NULL, NULL, 'bom-open-key', 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('INV', '发票税务', 'http://localhost:8089', 'API_KEY', NULL, NULL, 'inv-open-key', 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('CRM', '客户关系', 'http://localhost:8091', 'API_KEY', NULL, NULL, 'crm-open-key', 'MOCK', TRUE);
MERGE INTO ct_system (code, name, base_url, auth_type, username, password, api_key, mode, enabled)
KEY (code)
VALUES ('DMS', '经销商系统', 'http://localhost:8092', 'API_KEY', NULL, NULL, 'dms-open-key', 'MOCK', TRUE);

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
VALUES ('WMS_STUCK', '仓库拣货卡单', 'WMS_STUCK', '{"status":"NEW,PART_ALLOCATED,PICKING","hours":6}', 'HIGH', TRUE, 'WMS_ALLOCATE');
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
VALUES ('FORECAST_STOCKOUT', '预测即将缺货', 'FORECAST_STOCKOUT', '{"horizon":14}', 'MEDIUM', TRUE, 'SRM_PURCHASE_SUGGEST');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('UNSHIPPED_ORDER', '订单未发货', 'ORDER_STUCK', '{"status":"PAID","hours":24}', 'LOW', TRUE, 'OMS_PRIORITIZE');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('EXCEPTION_SHIPMENT', '运输异常', 'TMS_DELAY', '{"exception":true}', 'HIGH', TRUE, 'TMS_SYNC_TRACK');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('SAP_LOW_STOCK', 'ERP 库存偏低', 'EXT_STATUS', '{"system":"SAP","dataType":"STOCK","status":"LOW"}', 'HIGH', TRUE, 'SAP_CREATE_PR');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('SAP_PR_OPEN', 'ERP 采购申请待释放', 'EXT_STATUS', '{"system":"SAP","dataType":"PR","status":"CREATED"}', 'MEDIUM', TRUE, 'SAP_RELEASE_PR');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('SRM_PR_DRAFT', 'SRM 采购申请待提交', 'EXT_STATUS', '{"system":"SRM","dataType":"PR","status":"DRAFT"}', 'MEDIUM', TRUE, 'SRM_SUBMIT_PR');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('INV_REQUEST_DRAFT', '开票申请待提交', 'EXT_STATUS', '{"system":"INV","dataType":"INVOICE_REQUEST","status":"DRAFT"}', 'LOW', TRUE, 'INV_SUBMIT_REQUEST');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('INV_INPUT_UNVERIFIED', '进项发票待查验', 'EXT_STATUS', '{"system":"INV","dataType":"INPUT_INVOICE","status":"UNVERIFIED"}', 'MEDIUM', TRUE, 'INV_VERIFY_INPUT');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('BOM_ECN_DRAFT', '工程变更待提交', 'EXT_STATUS', '{"system":"BOM","dataType":"ECN","status":"DRAFT"}', 'MEDIUM', TRUE, 'BOM_SUBMIT_ECN');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('CRM_STALE_OPP', '商机停留在资格评估', 'EXT_STATUS', '{"system":"CRM","dataType":"OPPORTUNITY","status":"QUALIFICATION"}', 'MEDIUM', TRUE, 'CRM_ADVANCE_STAGE');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('CRM_OPEN_CASE', '客服工单待升级', 'EXT_STATUS', '{"system":"CRM","dataType":"CASE","status":"NEW"}', 'HIGH', TRUE, 'CRM_ESCALATE_CASE');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('DMS_PART_SHORTAGE', '经销商备件缺货', 'EXT_STATUS', '{"system":"DMS","dataType":"SHORTAGE","status":"SHORT"}', 'HIGH', TRUE, 'DMS_REPLENISH_SHORTAGE');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('OA_WF_PENDING', 'OA 审批待办积压', 'EXT_STATUS', '{"system":"OA","dataType":"WF_TASK","status":"PENDING"}', 'MEDIUM', TRUE, 'OA_APPROVE_TASK');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('SAP_MO_OPEN', 'ERP 生产订单待释放', 'EXT_STATUS', '{"system":"SAP","dataType":"MO","status":"CREATED"}', 'MEDIUM', TRUE, 'SAP_RELEASE_MO');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('TMS_OPEN_DISPATCH', '运输单待调度', 'TMS_OPEN', '{}', 'HIGH', TRUE, 'TMS_DISPATCH');

MERGE INTO ct_cost_target ("month", cost_type, target_amount)
KEY ("month", cost_type)
VALUES (FORMATDATETIME(CURRENT_DATE, 'yyyy-MM'), NULL, 100000.00);

MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('ASN_DELAY', '供应商到货延误', 'ASN_DELAY', '{"days":0}', 'HIGH', TRUE, 'SRM_EXPEDITE_PO');
MERGE INTO ct_rule (code, name, type, params, severity, enabled, suggested_action)
KEY (code)
VALUES ('SUPPLIER_RISK', '供应商绩效风险', 'SUPPLIER_RISK', '{"minScore":85}', 'MEDIUM', TRUE, 'SRM_EXPEDITE_PO');

MERGE INTO ct_objective (code, name, category, metric, direction, target_value, weight, unit, enabled)
KEY (code)
VALUES ('COST_PER_ORDER', '单均履约成本', 'COST', 'costPerOrder30d', 'MIN', 25.0, 30, 'CNY', TRUE);
MERGE INTO ct_objective (code, name, category, metric, direction, target_value, weight, unit, enabled)
KEY (code)
VALUES ('OTIF', '准时足量交付率', 'SERVICE', 'otif30d', 'MAX', 0.95, 25, '%', TRUE);
MERGE INTO ct_objective (code, name, category, metric, direction, target_value, weight, unit, enabled)
KEY (code)
VALUES ('NPS', '客户 NPS 估算', 'SERVICE', 'npsEstimate', 'MAX', 50, 25, 'pt', TRUE);
MERGE INTO ct_objective (code, name, category, metric, direction, target_value, weight, unit, enabled)
KEY (code)
VALUES ('STOCKOUT_RATE', '缺货 SKU 占比', 'INVENTORY', 'stockoutRate', 'MIN', 0.05, 10, '%', TRUE);
MERGE INTO ct_objective (code, name, category, metric, direction, target_value, weight, unit, enabled)
KEY (code)
VALUES ('SUPPLIER_OTD', '供应商准时到货率', 'SUPPLY', 'supplierOnTimeRate', 'MAX', 0.92, 10, '%', TRUE);
