package com.ir.integration.mock;

import com.ir.integration.client.ActionCommand;
import com.ir.integration.client.IntegrationException;
import com.ir.integration.client.SrmClient;
import com.ir.snapshot.PurchaseSnapshot;
import com.ir.snapshot.SupplierScore;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MockSrmClient implements SrmClient {
    static final List<String> SUPPLIERS = Arrays.asList("S001", "S002", "S003", "S004");
    private static final List<String> PLANTS = Arrays.asList("1000", "2000");

    private final MockDataset dataset;
    private final List<PurchaseSnapshot> purchaseOrders = new ArrayList<>();
    private final List<PurchaseSnapshot> asns = new ArrayList<>();
    private final List<SupplierScore> scores = new ArrayList<>();
    private final List<Map<String, Object>> requisitions = new ArrayList<>();
    private final AtomicInteger prSeq = new AtomicInteger(100);

    public MockSrmClient(MockDataset dataset) {
        this.dataset = dataset;
    }

    @PostConstruct
    public void generate() {
        LocalDate today = LocalDate.now();
        List<String> skus = dataset.skus();
        for (int i = 0; i < 24; i++) {
            PurchaseSnapshot po = new PurchaseSnapshot();
            po.setDocType("PO");
            po.setCode("PO" + String.format("%05d", i + 1));
            po.setSupplierCode(SUPPLIERS.get(i % SUPPLIERS.size()));
            po.setPlantCode(PLANTS.get(i % PLANTS.size()));
            po.setSku(skus.get(i % skus.size()));
            po.setQty(BigDecimal.valueOf(200 + (i % 5) * 50));
            po.setAmount(po.getQty().multiply(BigDecimal.valueOf(12 + i % 7)));
            po.setExpectedDate(today.plusDays((i % 9) - 3L));
            if (i % 6 == 0) {
                po.setStatus("CLOSED");
                po.setReceivedQty(po.getQty());
            } else if (i % 6 == 1) {
                po.setStatus("PARTIAL");
                po.setReceivedQty(po.getQty().divide(BigDecimal.valueOf(2)));
            } else {
                po.setStatus(i % 4 == 3 ? "SENT" : "CONFIRMED");
                po.setReceivedQty(BigDecimal.ZERO);
            }
            purchaseOrders.add(po);

            if (i % 3 != 0) {
                PurchaseSnapshot asn = new PurchaseSnapshot();
                asn.setDocType("ASN");
                asn.setCode("ASN" + String.format("%05d", i + 1));
                asn.setRefCode(po.getCode());
                asn.setSupplierCode(po.getSupplierCode());
                asn.setPlantCode(po.getPlantCode());
                asn.setSku(po.getSku());
                asn.setQty(po.getQty());
                asn.setExpectedDate(po.getExpectedDate());
                boolean received = "CLOSED".equals(po.getStatus()) || "PARTIAL".equals(po.getStatus());
                asn.setStatus(received ? "RECEIVED" : (i % 5 == 2 ? "DELAYED" : "IN_TRANSIT"));
                asn.setReceivedQty(received ? po.getReceivedQty() : BigDecimal.ZERO);
                asn.setReceivedAt(received ? LocalDateTime.now().minusDays(i % 4) : null);
                asns.add(asn);
            }
        }
        String period = today.getYear() + "-" + String.format("%02d", today.getMonthValue());
        double[] onTime = {0.98, 0.91, 0.76, 0.88};
        double[] quality = {0.99, 0.97, 0.90, 0.95};
        String[] grades = {"A", "B", "C", "B"};
        for (int i = 0; i < SUPPLIERS.size(); i++) {
            SupplierScore score = new SupplierScore();
            score.setSupplierCode(SUPPLIERS.get(i));
            score.setPeriod(period);
            score.setReceiptCount(6 + i * 2);
            score.setOnTimeRate(BigDecimal.valueOf(onTime[i]));
            score.setQtyAccuracy(BigDecimal.valueOf(0.97));
            score.setQualityRate(BigDecimal.valueOf(quality[i]));
            score.setAvgScore(BigDecimal.valueOf(
                    Math.round((onTime[i] * 0.5 + quality[i] * 0.3 + 0.97 * 0.2) * 1000) / 10.0));
            score.setGrade(grades[i]);
            scores.add(score);
        }
    }

    @Override
    public List<PurchaseSnapshot> fetchPurchaseOrders() {
        return new ArrayList<>(purchaseOrders);
    }

    @Override
    public List<PurchaseSnapshot> fetchAsns() {
        return new ArrayList<>(asns);
    }

    @Override
    public List<SupplierScore> fetchSupplierScores() {
        return new ArrayList<>(scores);
    }

    @Override
    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("purchaseOrders", purchaseOrders.size());
        result.put("asns", asns.size());
        result.put("requisitions", requisitions.size());
        return result;
    }

    @Override
    public synchronized Map<String, Object> execute(ActionCommand command) {
        Map<String, Object> result = new LinkedHashMap<>();
        if ("SRM_PURCHASE_SUGGEST".equals(command.getType())) {
            Map<String, Object> pr = new LinkedHashMap<>();
            pr.put("code", "PR" + prSeq.incrementAndGet());
            pr.put("sku", command.getParams().getOrDefault("sku", command.getTargetKey()));
            pr.put("qty", command.getParams().get("qty"));
            pr.put("status", "SUBMITTED");
            requisitions.add(pr);
            result.put("prCode", pr.get("code"));
            result.put("status", "SUBMITTED");
            return result;
        }
        if ("SRM_EXPEDITE_PO".equals(command.getType())) {
            for (PurchaseSnapshot po : purchaseOrders) {
                if (po.getCode().equals(command.getTargetKey())) {
                    po.setExpectedDate(LocalDate.now().plusDays(1));
                    result.put("expectedDate", po.getExpectedDate());
                    return result;
                }
            }
            throw new IntegrationException("SRM 采购订单不存在: " + command.getTargetKey());
        }
        throw new IntegrationException("SRM 不支持的动作: " + command.getType());
    }

    public List<Map<String, Object>> requisitions() {
        return new ArrayList<>(requisitions);
    }

    @Override
    public boolean health() {
        return true;
    }
}
