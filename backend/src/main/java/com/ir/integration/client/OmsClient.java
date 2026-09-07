package com.ir.integration.client;

import com.ir.snapshot.*;
import java.util.List;
import java.util.Map;

public interface OmsClient {
    List<OrderSnapshot> fetchOrders();
    List<InventorySnapshot> fetchInventory();
    List<SalesPoint> fetchDailySales(int days);
    Map<String, Object> dashboard();
    void execute(ActionCommand command);
    boolean health();
}
