package com.ir.integration.mock;

import com.ir.integration.client.*;
import com.ir.snapshot.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MockTmsClient implements TmsClient {
    private final DataStore store;
    public MockTmsClient(DataStore store) { this.store = store; }
    public List<ShipmentSnapshot> fetchWaybills() { return new ArrayList<>(store.shipments); }
    public List<CostRecord> fetchFreightBills() { List<CostRecord> out = new ArrayList<>(); for (CostRecord c : store.costs) if ("FREIGHT".equals(c.getCostType())) out.add(c); return out; }
    public Map<String,Object> dashboard() { Map<String,Object> m = new LinkedHashMap<>(); m.put("waybills", store.shipments.size()); return m; }
    public void execute(ActionCommand c) { ShipmentSnapshot s = store.shipment(c.getTargetKey()); if (s == null) return; if ("TMS_DISPATCH".equals(c.getType())) s.setStatus("DISPATCHED"); else if ("TMS_SYNC_TRACK".equals(c.getType())) { if (s.isExceptionFlag()) s.setExceptionFlag(false); } }
    public boolean health() { return true; }
}
