package com.ir.action;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ir.snapshot.ExtSnapshot;
import com.ir.snapshot.ExtSnapshotMapper;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.InventorySnapshotMapper;
import com.ir.snapshot.ShipmentSnapshot;
import com.ir.snapshot.ShipmentSnapshotMapper;
import com.ir.snapshot.WmsOrderSnapshot;
import com.ir.snapshot.WmsOrderSnapshotMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把预警/沙盘/补货传来的残缺指令补全成各系统 Open API 能执行的参数。
 * 例如 SAP_LOW_STOCK 的 bizKey 是 MAT-1000/1000/0001，真正物料号在快照 sku 字段。
 */
@Component
public class ActionEnricher {
    private final ExtSnapshotMapper extMapper;
    private final InventorySnapshotMapper inventoryMapper;
    private final ShipmentSnapshotMapper shipmentMapper;
    private final WmsOrderSnapshotMapper wmsMapper;

    public ActionEnricher(
            ExtSnapshotMapper extMapper,
            InventorySnapshotMapper inventoryMapper,
            ShipmentSnapshotMapper shipmentMapper,
            WmsOrderSnapshotMapper wmsMapper) {
        this.extMapper = extMapper;
        this.inventoryMapper = inventoryMapper;
        this.shipmentMapper = shipmentMapper;
        this.wmsMapper = wmsMapper;
    }

    public Map<String, Object> enrich(Map<String, Object> request) {
        Map<String, Object> result = copy(request);
        String type = str(result.get("type"));
        String targetKey = str(result.get("targetKey"));
        Map<String, Object> params = paramsOf(result);
        parseComposite(targetKey, params);
        ExtSnapshot ext = findExt(type, targetKey, params);
        InventorySnapshot inventory = findInventory(params, targetKey);

        if (ext != null) {
            put(params, "sku", ext.getSku());
            put(params, "plantCode", ext.getPlantCode());
            put(params, "title", ext.getTitle());
            put(params, "status", ext.getStatus());
            put(params, "dataType", ext.getDataType());
            if (qty(params) == null && ext.getQty() != null) {
                params.put("qty", suggestQty(type, ext.getStatus(), ext.getQty()));
            }
        }
        if (inventory != null) {
            put(params, "sku", inventory.getSku());
            put(params, "warehouseCode", inventory.getWarehouseCode());
            if (qty(params) == null) {
                BigDecimal gap = nz(inventory.getSafetyQty()).subtract(nz(inventory.getQtyAvailable()));
                params.put("qty", gap.signum() > 0 ? gap : BigDecimal.ONE);
            }
        }

        if (qty(params) != null) {
            put(params, "suggestQty", String.valueOf(qty(params)));
        }
        applyTypeHints(type, targetKey, params, ext, result);
        result.put("params", params);
        if (result.get("targetKey") == null || "null".equals(String.valueOf(result.get("targetKey")))) {
            result.put("targetKey", first(str(params.get("sku")), targetKey));
        }
        return result;
    }

    private void applyTypeHints(
            String type,
            String targetKey,
            Map<String, Object> params,
            ExtSnapshot ext,
            Map<String, Object> result) {
        if (type == null) {
            return;
        }
        if (type.startsWith("SAP_")) {
            put(params, "matnr", first(str(params.get("sku")), matnrOf(targetKey)));
            put(params, "werks", first(str(params.get("plantCode")), werksOf(targetKey), "1000"));
            if ("SAP_CREATE_PR".equals(type)) {
                String sku = first(str(params.get("sku")), str(params.get("matnr")), matnrOf(targetKey));
                if (sku != null) {
                    result.put("targetKey", sku);
                    params.put("sku", sku);
                }
                if (qty(params) == null) {
                    params.put("qty", BigDecimal.TEN);
                }
            } else if ("SAP_RELEASE_PR".equals(type)) {
                put(params, "banfn", first(str(params.get("banfn")),
                        ext != null && "PR".equals(ext.getDataType()) ? ext.getBizKey() : targetKey));
                result.put("targetKey", str(params.get("banfn")));
            } else if ("SAP_RELEASE_MO".equals(type)) {
                put(params, "aufnr", first(str(params.get("aufnr")), targetKey));
                result.put("targetKey", str(params.get("aufnr")));
            }
        } else if (type.startsWith("SRM_")) {
            if (type.contains("PR")) {
                put(params, "code", first(str(params.get("code")), targetKey));
            }
            put(params, "sku", first(str(params.get("sku")), targetKey));
            if (qty(params) == null) {
                params.put("qty", BigDecimal.ONE);
            }
        } else if ("WMS_REPLENISH".equals(type)) {
            String warehouse = wmsWarehouse(first(str(params.get("warehouseCode")), targetKey));
            params.put("warehouseCode", warehouse);
            result.put("targetKey", warehouse);
        } else if ("WMS_ALLOCATE".equals(type)) {
            WmsOrderSnapshot wms = findWms(targetKey);
            if (wms != null) {
                put(params, "orderCode", wms.getCode());
                result.put("targetKey", wms.getCode());
            }
        } else if ("TMS_SWITCH_CARRIER".equals(type) || "TMS_SYNC_TRACK".equals(type)
                || "TMS_DISPATCH".equals(type)) {
            ShipmentSnapshot shipment = findShipment(targetKey);
            if (shipment != null) {
                result.put("targetKey", shipment.getWaybillCode());
                put(params, "waybillId", shipment.getWaybillCode());
                if ("TMS_SWITCH_CARRIER".equals(type)) {
                    put(params, "carrierCode", first(str(params.get("carrierCode")), "SELF"));
                }
            }
        } else if (type.startsWith("INV_")) {
            put(params, "requestNo", first(str(params.get("requestNo")), targetKey));
            result.put("targetKey", str(params.get("requestNo")));
        } else if ("CRM_ADVANCE_STAGE".equals(type)) {
            put(params, "opportunityId", first(str(params.get("opportunityId")), targetKey));
        } else if ("CRM_ESCALATE_CASE".equals(type)) {
            put(params, "caseNo", first(str(params.get("caseNo")), targetKey));
        } else if ("DMS_REPLENISH_SHORTAGE".equals(type)) {
            String dealer = first(str(params.get("dealerCode")),
                    ext != null ? ext.getPlantCode() : null, dealerOf(targetKey));
            params.put("dealerCode", dealer);
            result.put("targetKey", first(dealer, targetKey));
            put(params, "sku", first(str(params.get("sku")), skuOfDealerKey(targetKey)));
        } else if ("BOM_EXPLODE".equals(type)) {
            put(params, "bomNo", first(str(params.get("bomNo")),
                    ext != null ? ext.getBizKey() : targetKey));
            result.put("targetKey", str(params.get("bomNo")));
        } else if ("OA_START_WORKFLOW".equals(type)) {
            put(params, "definitionCode", first(str(params.get("definitionCode")), "GENERAL"));
            put(params, "businessType", first(str(params.get("businessType")), "IR"));
            put(params, "businessId", first(str(params.get("businessId")), targetKey));
            put(params, "title", first(str(params.get("title")), "IR 控制塔审批 " + targetKey));
        }
    }

    private void parseComposite(String targetKey, Map<String, Object> params) {
        if (targetKey == null || !targetKey.contains("/")) {
            return;
        }
        String[] parts = targetKey.split("/");
        if (parts.length >= 3 && parts[0].startsWith("MAT-")) {
            put(params, "sku", parts[0]);
            put(params, "matnr", parts[0]);
            put(params, "werks", parts[1]);
            put(params, "plantCode", parts[1]);
            put(params, "lgort", parts[2]);
        } else if (parts.length >= 2 && parts[1].startsWith("WH-")) {
            put(params, "sku", parts[0]);
            put(params, "warehouseCode", parts[1]);
        } else if (parts.length >= 2 && parts[0].startsWith("D")) {
            put(params, "dealerCode", parts[0]);
            put(params, "sku", parts[1]);
        }
    }

    private ExtSnapshot findExt(String type, String targetKey, Map<String, Object> params) {
        if (targetKey != null) {
            ExtSnapshot byKey = extMapper.selectOne(new LambdaQueryWrapper<ExtSnapshot>()
                    .eq(ExtSnapshot::getBizKey, targetKey)
                    .last("LIMIT 1"));
            if (byKey != null) {
                return byKey;
            }
        }
        String sku = first(str(params.get("sku")), str(params.get("matnr")));
        String system = systemHint(type);
        if (sku != null) {
            LambdaQueryWrapper<ExtSnapshot> query = new LambdaQueryWrapper<ExtSnapshot>()
                    .eq(ExtSnapshot::getSku, sku);
            if (system != null) {
                query.eq(ExtSnapshot::getSourceSystem, system);
            }
            query.orderByDesc(ExtSnapshot::getId).last("LIMIT 1");
            ExtSnapshot bySku = extMapper.selectOne(query);
            if (bySku != null) {
                return bySku;
            }
        }
        return null;
    }

    private InventorySnapshot findInventory(Map<String, Object> params, String targetKey) {
        String sku = first(str(params.get("sku")), skuIfPlain(targetKey));
        if (sku == null) {
            return null;
        }
        String warehouse = str(params.get("warehouseCode"));
        LambdaQueryWrapper<InventorySnapshot> query = new LambdaQueryWrapper<InventorySnapshot>()
                .eq(InventorySnapshot::getSku, sku);
        if (warehouse != null) {
            query.eq(InventorySnapshot::getWarehouseCode, warehouse);
        }
        query.orderByAsc(InventorySnapshot::getQtyAvailable).last("LIMIT 1");
        return inventoryMapper.selectOne(query);
    }

    private WmsOrderSnapshot findWms(String targetKey) {
        if (targetKey == null) {
            return null;
        }
        WmsOrderSnapshot byCode = wmsMapper.selectOne(new LambdaQueryWrapper<WmsOrderSnapshot>()
                .eq(WmsOrderSnapshot::getCode, targetKey)
                .last("LIMIT 1"));
        if (byCode != null) {
            return byCode;
        }
        return wmsMapper.selectOne(new LambdaQueryWrapper<WmsOrderSnapshot>()
                .eq(WmsOrderSnapshot::getExternalNo, targetKey)
                .last("LIMIT 1"));
    }

    private ShipmentSnapshot findShipment(String targetKey) {
        if (targetKey != null) {
            ShipmentSnapshot byCode = shipmentMapper.selectOne(new LambdaQueryWrapper<ShipmentSnapshot>()
                    .eq(ShipmentSnapshot::getWaybillCode, targetKey)
                    .last("LIMIT 1"));
            if (byCode != null) {
                return byCode;
            }
            ShipmentSnapshot byOrder = shipmentMapper.selectOne(new LambdaQueryWrapper<ShipmentSnapshot>()
                    .eq(ShipmentSnapshot::getSourceNo, targetKey)
                    .last("LIMIT 1"));
            if (byOrder != null) {
                return byOrder;
            }
        }
        for (ShipmentSnapshot shipment : shipmentMapper.selectList(null)) {
            if (Arrays.asList("DELIVERED", "CLOSED", "CANCELLED").contains(shipment.getStatus())) {
                continue;
            }
            return shipment;
        }
        return null;
    }

    private static BigDecimal suggestQty(String type, String status, BigDecimal onHand) {
        if ("SAP_CREATE_PR".equals(type) || "SRM_PURCHASE_SUGGEST".equals(type)
                || "WMS_REPLENISH".equals(type) || "DMS_REPLENISH_SHORTAGE".equals(type)) {
            if ("LOW".equals(status) || "SHORT".equals(status)) {
                BigDecimal gap = BigDecimal.valueOf(20).subtract(nz(onHand));
                return gap.signum() > 0 ? gap : BigDecimal.TEN;
            }
        }
        return onHand == null || onHand.signum() <= 0 ? BigDecimal.ONE : onHand;
    }

    private static String wmsWarehouse(String value) {
        if (value != null && (value.startsWith("WH-") || value.startsWith("WH0"))) {
            return value;
        }
        return "WH-SH";
    }

    private static String systemHint(String type) {
        if (type == null) {
            return null;
        }
        if (type.startsWith("SAP_")) {
            return "SAP";
        }
        if (type.startsWith("SRM_")) {
            return "SRM";
        }
        if (type.startsWith("BOM_")) {
            return "BOM";
        }
        if (type.startsWith("INV_")) {
            return "INV";
        }
        if (type.startsWith("CRM_")) {
            return "CRM";
        }
        if (type.startsWith("DMS_")) {
            return "DMS";
        }
        if (type.startsWith("OA_")) {
            return "OA";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paramsOf(Map<String, Object> request) {
        Object value = request.get("params");
        Map<String, Object> params = value instanceof Map
                ? new LinkedHashMap<String, Object>((Map<String, Object>) value)
                : new LinkedHashMap<String, Object>();
        for (String key : Arrays.asList("sku", "qty", "suggestQty", "warehouseCode", "plantCode",
                "carrierCode", "dealerCode", "matnr", "werks")) {
            if (request.get(key) != null && !params.containsKey(key)) {
                params.put(key, request.get(key));
            }
        }
        return params;
    }

    private static Map<String, Object> copy(Map<String, Object> request) {
        return request == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<>(request);
    }

    private static void put(Map<String, Object> params, String key, String value) {
        if (value != null && (params.get(key) == null || blank(str(params.get(key))))) {
            params.put(key, value);
        }
    }

    private static BigDecimal qty(Map<String, Object> params) {
        Object value = params.get("qty") != null ? params.get("qty") : params.get("suggestQty");
        if (value == null || blank(String.valueOf(value))) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String matnrOf(String targetKey) {
        if (targetKey == null) {
            return null;
        }
        int slash = targetKey.indexOf('/');
        return slash > 0 ? targetKey.substring(0, slash) : targetKey;
    }

    private static String werksOf(String targetKey) {
        if (targetKey == null) {
            return null;
        }
        String[] parts = targetKey.split("/");
        return parts.length >= 2 ? parts[1] : null;
    }

    private static String dealerOf(String targetKey) {
        if (targetKey == null) {
            return null;
        }
        int slash = targetKey.indexOf('/');
        return slash > 0 ? targetKey.substring(0, slash) : targetKey;
    }

    private static String skuOfDealerKey(String targetKey) {
        if (targetKey == null) {
            return null;
        }
        int slash = targetKey.indexOf('/');
        return slash > 0 ? targetKey.substring(slash + 1) : null;
    }

    private static String skuIfPlain(String targetKey) {
        if (targetKey == null || targetKey.contains("/")) {
            return null;
        }
        return targetKey;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (!blank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty() || "null".equals(value);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
