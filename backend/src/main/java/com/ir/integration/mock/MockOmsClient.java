package com.ir.integration.mock;

import com.ir.integration.client.*;
import com.ir.snapshot.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MockOmsClient implements OmsClient {
    private final DataStore store;
    public MockOmsClient(DataStore store) { this.store = store; }
    public List<OrderSnapshot> fetchOrders() { return new ArrayList<>(store.orders); }
    public List<InventorySnapshot> fetchInventory() { return new ArrayList<>(store.inventory); }
    public List<SalesPoint> fetchDailySales(int days) { return new ArrayList<>(store.sales); }
    public Map<String, Object> dashboard() { Map<String,Object> m = new LinkedHashMap<>(); m.put("orders", store.orders.size()); return m; }
    public void execute(ActionCommand c) {
        OrderSnapshot o = store.order(c.getTargetKey()); if (o == null) return;
        if ("OMS_HOLD".equals(c.getType())) o.setStatus("HOLD");
        else if ("OMS_UNHOLD".equals(c.getType())) o.setStatus("AUDITED");
        else if ("OMS_PRIORITIZE".equals(c.getType())) { }
        else if ("OMS_CANCEL".equals(c.getType())) o.setStatus("CANCELLED");
        else if ("OMS_REROUTE_WAREHOUSE".equals(c.getType()) && c.getParams() != null) o.setWarehouseCode(String.valueOf(c.getParams().get("warehouseCode")));
    }
    public boolean health() { return true; }
}
