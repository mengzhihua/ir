package com.ir.integration.mock;

import com.ir.integration.client.BmsClient;
import com.ir.snapshot.*;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;

@Component
public class MockBmsClient implements BmsClient {
    private final DataStore store;
    public MockBmsClient(DataStore store) { this.store = store; }
    public List<CostRecord> fetchCosts(LocalDate from, LocalDate to) { List<CostRecord> out = new ArrayList<>(); for (CostRecord c : store.costs) if (!c.getBizDate().isBefore(from) && !c.getBizDate().isAfter(to)) out.add(c); return out; }
    public boolean health() { return true; }
}
