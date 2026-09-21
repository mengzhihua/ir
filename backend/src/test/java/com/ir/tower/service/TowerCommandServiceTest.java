package com.ir.tower.service;

import com.ir.alert.entity.CtAlert;
import com.ir.alert.mapper.CtAlertMapper;
import com.ir.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:towercommand;MODE=MySQL;DB_CLOSE_DELAY=-1")
class TowerCommandServiceTest {
    @Autowired
    private TowerCommandService commands;
    @Autowired
    private CtAlertMapper alerts;

    @Test
    void queueKeepsOlderHighAlertAheadOfNewerLowAlerts() {
        alerts.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CtAlert>()
                .eq(CtAlert::getStatus, "OPEN"));
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 40; i++) {
            alerts.insert(alert("ALT-LOW-" + i, "LOW", now.minusMinutes(i)));
        }
        CtAlert high = alert("ALT-HIGH-OLD", "HIGH", now.minusDays(10));
        alerts.insert(high);
        alerts.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CtAlert>()
                .eq(CtAlert::getId, high.getId())
                .set(CtAlert::getCreatedAt, now.minusDays(10)));
        Map<String, Object> queue = commands.queue(null);
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> items =
                (java.util.List<Map<String, Object>>) queue.get("nextActions");
        assertTrue(items.stream().anyMatch(row ->
                "HIGH".equals(row.get("severity"))
                        && high.getId().equals(asLong(row.get("id")))));
    }

    @Test
    void fractionalIdIsRejected() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("kind", "ALERT");
        request.put("id", 1.9);
        BizException ex = assertThrows(BizException.class, () -> commands.execute(request));
        assertEquals("id 必须为整数", ex.getMessage());
    }

    private static Long asLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private static CtAlert alert(String no, String severity, LocalDateTime createdAt) {
        CtAlert alert = new CtAlert();
        alert.setAlertNo(no);
        alert.setRuleCode("OMS_STUCK");
        alert.setType("ORDER_STUCK");
        alert.setSeverity(severity);
        alert.setTargetType("ORDER");
        alert.setTargetKey(no);
        alert.setTitle(no);
        alert.setStatus("OPEN");
        alert.setSuggestedAction("OMS_HOLD");
        alert.setCreatedAt(createdAt);
        return alert;
    }
}
