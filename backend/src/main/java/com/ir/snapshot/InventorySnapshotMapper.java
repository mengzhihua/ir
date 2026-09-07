package com.ir.snapshot;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface InventorySnapshotMapper extends BaseMapper<InventorySnapshot> {
    @Select("SELECT warehouse_code AS warehouseCode, sku, SUM(qty_on_hand) AS qtyOnHand, " +
            "SUM(qty_reserved) AS qtyReserved, SUM(qty_available) AS qtyAvailable, " +
            "SUM(safety_qty) AS safetyQty FROM ct_inventory_snapshot " +
            "GROUP BY warehouse_code, sku ORDER BY sku, warehouse_code")
    List<Map<String, Object>> summary();
}
