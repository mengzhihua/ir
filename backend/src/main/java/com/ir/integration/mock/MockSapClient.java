package com.ir.integration.mock;

import com.ir.integration.client.SapClient;
import com.ir.snapshot.FinanceSnapshot;
import com.ir.snapshot.InventorySnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MockSapClient implements SapClient {
    private static final List<String> PLANTS = Arrays.asList("1000", "2000");
    private final MockDataset dataset;

    public MockSapClient(MockDataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public List<InventorySnapshot> fetchStock() {
        List<InventorySnapshot> result = new ArrayList<>();
        List<String> skus = dataset.skus();
        for (int p = 0; p < PLANTS.size(); p++) {
            for (int s = 0; s < skus.size(); s++) {
                InventorySnapshot item = new InventorySnapshot();
                item.setSourceSystem("SAP");
                item.setWarehouseCode(PLANTS.get(p));
                item.setSku(skus.get(s));
                BigDecimal qty = BigDecimal.valueOf(400 + p * 150 + s * 60);
                item.setQtyOnHand(qty);
                item.setQtyReserved(BigDecimal.ZERO);
                item.setQtyAvailable(qty);
                item.setSafetyQty(BigDecimal.ZERO);
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public List<FinanceSnapshot> fetchFinance() {
        List<FinanceSnapshot> result = new ArrayList<>();
        result.add(finance("AP_OPEN", "ALL", BigDecimal.valueOf(186500.00), 14));
        result.add(finance("AR_OPEN", "ALL", BigDecimal.valueOf(242300.00), 21));
        result.add(finance("STOCK_VALUE", "ALL", BigDecimal.valueOf(1289000.00), 10));
        result.add(finance("MONTH_COST", "ALL", BigDecimal.valueOf(96500.00), 0));
        result.add(finance("MONTH_REVENUE", "ALL", BigDecimal.valueOf(438000.00), 0));
        return result;
    }

    @Override
    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stockValue", 1289000.00);
        result.put("apOpen", 14);
        result.put("arOpen", 21);
        return result;
    }

    @Override
    public boolean health() {
        return true;
    }

    private FinanceSnapshot finance(String metric, String dimension, BigDecimal amount, int count) {
        FinanceSnapshot row = new FinanceSnapshot();
        row.setMetric(metric);
        row.setDimension(dimension);
        row.setAmount(amount);
        row.setItemCount(count);
        return row;
    }
}
