package com.ir.integration.mock;

import org.springframework.stereotype.Component;
import com.ir.integration.client.BmsClient;
import com.ir.snapshot.entity.CostRecord;
import com.ir.snapshot.entity.ShipmentSnapshot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class MockBmsClient implements BmsClient {
    private final MockDataset dataset;

    public MockBmsClient(MockDataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public List<CostRecord> fetchCosts(LocalDate from, LocalDate to) {
        List<CostRecord> result = new ArrayList<>();
        for (CostRecord cost : dataset.costs()) {
            if (!cost.getBizDate().isBefore(from) && !cost.getBizDate().isAfter(to)) {
                result.add(cost);
            }
        }
        for (ShipmentSnapshot shipment : dataset.shipments()) {
            if (shipment.getPlannedArriveTime() == null) {
                continue;
            }
            LocalDate date = shipment.getPlannedArriveTime().toLocalDate();
            if (date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            CostRecord freight = new CostRecord();
            freight.setBizDate(date);
            freight.setOrderNo(shipment.getSourceNo());
            freight.setCarrierCode(shipment.getCarrierCode());
            freight.setCostType("FREIGHT");
            freight.setAmount(shipment.getFreightAmount());
            freight.setSourceSystem("BMS");
            result.add(freight);
        }
        return result;
    }

    @Override
    public boolean health() {
        return true;
    }
}
