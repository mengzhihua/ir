package com.ir.integration.mock;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.TmsClient;
import com.ir.snapshot.CostRecord;
import com.ir.snapshot.ShipmentSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MockTmsClient implements TmsClient {
    private final MockDataset dataset;

    public MockTmsClient(MockDataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public List<ShipmentSnapshot> fetchWaybills() {
        return new ArrayList<>(dataset.shipments());
    }

    @Override
    public List<CostRecord> fetchFreightBills() {
        List<CostRecord> freight = new ArrayList<>();
        for (CostRecord cost : dataset.costs()) {
            if ("FREIGHT".equals(cost.getCostType())) {
                freight.add(cost);
            }
        }
        for (ShipmentSnapshot shipment : dataset.shipments()) {
            CostRecord cost = new CostRecord();
            cost.setBizDate(shipment.getPlannedArriveTime().toLocalDate());
            cost.setOrderNo(shipment.getSourceNo());
            cost.setCarrierCode(shipment.getCarrierCode());
            cost.setCostType("FREIGHT");
            cost.setAmount(shipment.getFreightAmount());
            cost.setSourceSystem("TMS");
            freight.add(cost);
        }
        return freight;
    }

    @Override
    public Map<String, Object> dashboard() {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("waybills", dataset.shipments().size());
        return dashboard;
    }

    @Override
    public void execute(ActionCommand command) {
        for (ShipmentSnapshot shipment : dataset.shipments()) {
            if (!command.getTargetKey().equals(shipment.getWaybillCode())
                    && !command.getTargetKey().equals(shipment.getSourceNo())) {
                continue;
            }
            if ("TMS_SYNC_TRACK".equals(command.getType())) {
                shipment.setStatus("IN_TRANSIT");
            }
            if ("TMS_SWITCH_CARRIER".equals(command.getType())) {
                Object carrier = command.getParams().get("carrierCode");
                if (carrier != null) {
                    shipment.setCarrierCode(String.valueOf(carrier));
                }
            }
        }
    }

    @Override
    public boolean health() {
        return true;
    }
}
