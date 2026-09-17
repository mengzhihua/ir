package com.ir.integration.client;

import com.ir.snapshot.entity.CostRecord;
import com.ir.snapshot.entity.ShipmentSnapshot;
import java.util.List;
import java.util.Map;

public interface TmsClient {
    List<ShipmentSnapshot> fetchWaybills();
    List<CostRecord> fetchFreightBills();
    Map<String, Object> dashboard();
    void execute(ActionCommand command);
    boolean health();
}
