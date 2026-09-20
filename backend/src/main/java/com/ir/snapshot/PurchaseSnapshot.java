package com.ir.snapshot;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ir.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ct_purchase_snapshot")
public class PurchaseSnapshot extends BaseEntity {
    public static final int SKU_MAX_LENGTH = 1024;

    private String docType;
    private String code;
    private String refCode;
    private String supplierCode;
    private String plantCode;
    private String sku;
    private String status;
    private BigDecimal qty;
    private BigDecimal receivedQty;
    private BigDecimal amount;
    private LocalDate expectedDate;
    private LocalDateTime receivedAt;
    private LocalDateTime syncedAt;

    /** 多行单据的 sku 以逗号拼接存储,此处拆分为列表. */
    public List<String> skuList() {
        return sku == null || sku.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(sku.split(","));
    }

    /** 单行单据的 sku;多行时返回首行. */
    public String primarySku() {
        List<String> list = skuList();
        return list.isEmpty() ? null : list.get(0);
    }
}
