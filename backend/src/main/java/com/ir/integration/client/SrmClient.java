package com.ir.integration.client;

import com.ir.snapshot.PurchaseSnapshot;
import com.ir.snapshot.SupplierScore;
import java.util.List;
import java.util.Map;

public interface SrmClient {
    List<PurchaseSnapshot> fetchPurchaseOrders();
    List<PurchaseSnapshot> fetchAsns();
    List<SupplierScore> fetchSupplierScores();
    Map<String, Object> dashboard();
    Map<String, Object> execute(ActionCommand command);
    boolean health();
}
