package com.ir.integration.mock;

import org.springframework.stereotype.Component;
import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.OmsClient;
import com.ir.snapshot.entity.InventorySnapshot;
import com.ir.snapshot.entity.OrderSnapshot;
import com.ir.snapshot.entity.SalesPoint;
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
        for (OrderSnapshot order : dataset.orders()) {
            if (!command.getTargetKey().equals(order.getOrderNo())) {
                continue;
            }
            if ("OMS_HOLD".equals(command.getType())) {
                order.setStatus("HOLD");
            } else if ("OMS_UNHOLD".equals(command.getType())) {
                order.setStatus("AUDITED");
            } else if ("OMS_AUTO_PROCESS".equals(command.getType())) {
                order.setStatus("ALLOCATED");
            } else if ("OMS_PRIORITIZE".equals(command.getType())) {
                int priority = 10;
                if (command.getParams() != null && command.getParams().get("priority") != null) {
                    try {
                        priority = Integer.parseInt(String.valueOf(command.getParams().get("priority")));
                    } catch (NumberFormatException ignored) {
                        priority = 10;
                    }
                }
                order.setPriority(priority);
            } else if ("OMS_REROUTE_WAREHOUSE".equals(command.getType())
                    && command.getParams() != null
                    && command.getParams().get("warehouseCode") != null) {
                order.setWarehouseCode(String.valueOf(command.getParams().get("warehouseCode")));
            } else if ("OMS_CANCEL".equals(command.getType())) {
                order.setStatus("CANCELLED");
            }
        }
    }

    @Override
    public boolean health() {
        return true;
    }
}
