package com.ir.sandbox.engine;

import com.ir.snapshot.entity.InventorySnapshot;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大规模沙盘目录：最多 10 万 SKU、单 SKU 库存最多 1000 万。
 * 不写数据库，只生成引擎基线，用来压资金盘量级。
 */
public final class ScaleCatalog {
    private static final List<String> WAREHOUSES = Arrays.asList("WH-SH", "WH-BJ", "WH-GZ");
    private static final List<String> REGIONS = Arrays.asList("华东", "华北", "华南");

    private ScaleCatalog() {
    }

    public static BaselineData build(Integer skuCount, BigDecimal qtyPerSku) {
        int skus = CapitalTiers.clampSku(skuCount);
        BigDecimal qty = CapitalTiers.clampQty(qtyPerSku == null
                ? BigDecimal.valueOf(1000) : qtyPerSku);
        BaselineData data = new BaselineData();
        BigDecimal daily = qty.divide(BigDecimal.valueOf(200), 2, java.math.RoundingMode.HALF_UP)
                .max(BigDecimal.ONE);
        List<BigDecimal> history = Collections.nCopies(14, daily);
        Map<String, BigDecimal> sharedChannel = Collections.singletonMap("ALL", BigDecimal.ONE);
        for (int i = 0; i < skus; i++) {
            String sku = skuCode(i);
            String warehouse = WAREHOUSES.get(i % WAREHOUSES.size());
            String region = REGIONS.get(i % REGIONS.size());
            InventorySnapshot item = new InventorySnapshot();
            item.setWarehouseCode(warehouse);
            item.setSku(sku);
            item.setQtyAvailable(qty);
            item.setQtyOnHand(qty);
            data.getInventory().add(item);
            data.getDemandBySku().put(sku, history);
            data.getSkuWarehouse().put(sku, warehouse);
            data.getChannelShare().put(sku, sharedChannel);
            data.getRegionShare().put(sku, Collections.singletonMap(region, BigDecimal.ONE));
        }
        data.getRegionShare().put("*", new LinkedHashMap<String, BigDecimal>(
                Collections.singletonMap("华东", BigDecimal.ONE)));
        return data;
    }

    static String skuCode(int index) {
        return String.format("SKU-S%05d", index + 1);
    }
}
