package com.ir.integration.mock;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.WmsClient;
import com.ir.snapshot.InventorySnapshot;
import com.ir.snapshot.WmsOrderSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MockWmsClient implements WmsClient {
    private final MockDataset dataset;

    public MockWmsClient(MockDataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public List<WmsOrderSnapshot> fetchOutbound() {
        return new ArrayList<>(dataset.outbound());
    }

    @Override
    public List<InventorySnapshot> fetchInventorySummary() {
        return new ArrayList<>(dataset.inventory());
    }

    @Override
    public Map<String, Object> dashboard() {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("outboundOrders", dataset.outbound().size());
        dashboard.put("inventoryRows", dataset.inventory().size());
        return dashboard;
    }

    @Override
    public void execute(ActionCommand command) {
        for (WmsOrderSnapshot order : dataset.outbound()) {
            if (!command.getTargetKey().equals(order.getCode())
                    && !command.getTargetKey().equals(order.getExternalNo())) {
                continue;
            }
            if ("WMS_ALLOCATE".equals(command.getType())) {
                order.setStatus("ALLOCATED");
            }
        }
    }

    @Override
    public boolean health() {
        return true;
    }
}
