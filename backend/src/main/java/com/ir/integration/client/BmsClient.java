package com.ir.integration.client;

import com.ir.snapshot.CostRecord;
import java.time.LocalDate;
import java.util.List;

public interface BmsClient {
    List<CostRecord> fetchCosts(LocalDate from, LocalDate to);
    boolean health();
}
