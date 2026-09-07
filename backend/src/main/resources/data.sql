MERGE INTO ct_system (code,name,base_url,auth_type,mode,enabled) KEY(code) VALUES
 ('OMS','订单管理系统','http://localhost:8081','BEARER','MOCK',TRUE),
 ('TMS','运输管理系统','http://localhost:8082','NONE','MOCK',TRUE),
 ('WMS','仓储管理系统','http://localhost:8083','BEARER','MOCK',TRUE),
 ('BMS','费用管理系统','http://localhost:8084','API_KEY','MOCK',TRUE),
 ('SRM','供应商管理系统/采购','http://localhost:8085','API_KEY','MOCK',FALSE);
MERGE INTO ct_rule (code,name,type,params,severity,enabled,suggested_action) KEY(code) VALUES
 ('ORDER_AUDITED_STUCK','订单审核卡单','ORDER_STUCK','{"status":"AUDITED","hours":4}','HIGH',TRUE,'OMS_HOLD'),
 ('WMS_PICKING_STUCK','WMS拣货卡单','WMS_STUCK','{"status":"PICKING","hours":6}','HIGH',TRUE,'WMS_ALLOCATE'),
 ('TMS_DELAY','运输到达延迟','TMS_DELAY','{"hours":0}','HIGH',TRUE,'TMS_SYNC_TRACK'),
 ('LOW_STOCK','低库存','LOW_STOCK','{"ratio":1}','MEDIUM',TRUE,'WMS_REPLENISH'),
 ('COST_OVERRUN','成本超标','COST_OVERRUN','{"ratio":1.2}','MEDIUM',TRUE,'SRM_PURCHASE_SUGGEST'),
 ('FORECAST_STOCKOUT','预测缺货','FORECAST_STOCKOUT','{"days":3}','MEDIUM',TRUE,'SRM_PURCHASE_SUGGEST'),
 ('ORDER_UNSHIPPED','未发货订单','ORDER_STUCK','{"status":"ALLOCATED","hours":24}','LOW',TRUE,'OMS_AUTO_PROCESS'),
 ('EXCEPTION_SHIPMENT','异常运输','TMS_DELAY','{"exceptionFlag":true}','MEDIUM',TRUE,'TMS_SYNC_TRACK');
