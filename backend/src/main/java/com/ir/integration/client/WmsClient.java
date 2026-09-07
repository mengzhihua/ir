package com.ir.integration.client;

import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.WmsOrderSnapshot;
import java.util.List;
import java.util.Map;

public interface WmsClient {
    List<WmsOrderSnapshot> fetchOutbound();
    List<InventorySnapshot> fetchInventorySummary();
    Map<String, Object> dashboard();
    void execute(ActionCommand command);
    boolean health();
}
