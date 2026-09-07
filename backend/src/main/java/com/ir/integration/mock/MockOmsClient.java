package com.ir.integration.mock;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.OmsClient;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.SalesPoint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MockOmsClient implements OmsClient {
    private final MockDataset dataset;

    public MockOmsClient(MockDataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public List<OrderSnapshot> fetchOrders() {
        return new ArrayList<>(dataset.orders());
    }

    @Override
    public List<InventorySnapshot> fetchInventory() {
        return new ArrayList<>(dataset.inventory());
    }

    @Override
    public List<SalesPoint> fetchDailySales(int days) {
        return new ArrayList<>(dataset.sales());
    }

    @Override
    public Map<String, Object> dashboard() {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("orders", dataset.orders().size());
        dashboard.put("todayOrders", dataset.orders().size() / 60);
        return dashboard;
    }

    @Override
    public void execute(ActionCommand command) {
        if ("OMS_HOLD".equals(command.getType())
                || "OMS_PRIORITIZE".equals(command.getType())) {
            for (OrderSnapshot order : dataset.orders()) {
                if (command.getTargetKey().equals(order.getOrderNo())) {
                    order.setStatus("HOLD".equals(command.getType()) ? "HOLD" : order.getStatus());
                }
            }
        }
    }

    @Override
    public boolean health() {
        return true;
    }
}
