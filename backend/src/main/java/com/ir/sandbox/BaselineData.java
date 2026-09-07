package com.ir.sandbox;

import com.ir.snapshot.InventorySnapshot;
import lombok.Data;
import java.math.BigDecimal;
import java.util.*;

@Data
public class BaselineData {
    private List<InventorySnapshot> inventory=new ArrayList<>();
    private Map<String,List<BigDecimal>> demandBySku=new LinkedHashMap<>();
    private Map<String,String> skuWarehouse=new LinkedHashMap<>();
    public static BaselineData from(com.ir.snapshot.DataStore store){BaselineData b=new BaselineData();b.inventory.addAll(store.inventory);for(String sku:Arrays.asList("SKU001","SKU002","SKU003","SKU004","SKU005")){List<BigDecimal>x=new ArrayList<>();for(com.ir.snapshot.SalesPoint p:store.sales)if(sku.equals(p.getSku())&&"ALL".equals(p.getChannelCode()))x.add(p.getQty());b.demandBySku.put(sku,x);}return b;}
}
