package com.ir.sandbox;

import com.ir.snapshot.InventorySnapshot;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class BaselineData {
    private List<InventorySnapshot> inventory = new ArrayList<>();
    private Map<String, List<BigDecimal>> demandBySku = new LinkedHashMap<>();
    private Map<String, String> skuWarehouse = new LinkedHashMap<>();
    private Map<String, Map<String, BigDecimal>> channelRatios = new LinkedHashMap<>();
}
