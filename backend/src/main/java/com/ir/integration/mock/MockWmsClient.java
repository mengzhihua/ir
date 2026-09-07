package com.ir.integration.mock;

import com.ir.integration.client.*;
import com.ir.snapshot.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MockWmsClient implements WmsClient {
    private final DataStore store;
    public MockWmsClient(DataStore store) { this.store = store; }
    public List<WmsOrderSnapshot> fetchOutbound() { return new ArrayList<>(store.wmsOrders); }
    public List<InventorySnapshot> fetchInventorySummary() { return new ArrayList<>(store.inventory); }
    public Map<String,Object> dashboard() { Map<String,Object> m = new LinkedHashMap<>(); m.put("orders", store.wmsOrders.size()); return m; }
    public void execute(ActionCommand c) {
        if ("WMS_ALLOCATE".equals(c.getType())) { WmsOrderSnapshot w = store.wms(c.getTargetKey()); if (w != null && "NEW".equals(w.getStatus())) w.setStatus("ALLOCATED"); }
        if ("WMS_REPLENISH".equals(c.getType()) && c.getParams() != null) for (InventorySnapshot x : store.inventory) if (String.valueOf(c.getParams().get("warehouseCode")).equals(x.getWarehouseCode())) x.setQtyAvailable(x.getQtyAvailable().add(x.getSafetyQty()));
    }
    public boolean health() { return true; }
}
