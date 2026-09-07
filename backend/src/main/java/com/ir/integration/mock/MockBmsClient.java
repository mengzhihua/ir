package com.ir.integration.mock;

import com.ir.integration.client.BmsClient;
import com.ir.snapshot.CostRecord;
import org.springframework.stereotype.Component;

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
        return result;
    }

    @Override
    public boolean health() {
        return true;
    }
}
