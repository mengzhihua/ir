package com.ir.integration.client;

import com.ir.snapshot.FinanceSnapshot;
import com.ir.snapshot.InventorySnapshot;
import java.util.List;
import java.util.Map;

public interface SapClient {
    List<InventorySnapshot> fetchStock();
    List<FinanceSnapshot> fetchFinance();
    Map<String, Object> dashboard();
    boolean health();
}
