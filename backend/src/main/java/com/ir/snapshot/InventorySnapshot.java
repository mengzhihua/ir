package com.ir.snapshot;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_inventory_snapshot")
public class InventorySnapshot extends BaseEntity {
    private String sourceSystem;
    private String warehouseCode;
    private String sku;
    private BigDecimal qtyOnHand;
    private BigDecimal qtyReserved;
    private BigDecimal qtyAvailable;
    private BigDecimal safetyQty;
    private LocalDateTime syncedAt;
}
