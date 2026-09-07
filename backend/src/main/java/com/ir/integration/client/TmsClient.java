package com.ir.integration.client;

import com.ir.snapshot.CostRecord;
import com.ir.snapshot.ShipmentSnapshot;
import java.util.List;
import java.util.Map;

public interface TmsClient {
    List<ShipmentSnapshot> fetchWaybills();
    List<CostRecord> fetchFreightBills();
    Map<String, Object> dashboard();
    void execute(ActionCommand command);
    boolean health();
}
