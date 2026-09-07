package com.ir.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ir.integration.client.ClientFactory;
import com.ir.integration.client.OmsClient;
import com.ir.integration.sync.SystemSyncWorker;
import com.ir.snapshot.OrderSnapshot;
import com.ir.snapshot.OrderSnapshotMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:syncworker;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class SystemSyncWorkerTest {
    @Autowired
    private SystemSyncWorker worker;
    @Autowired
    private OrderSnapshotMapper orders;
    @MockBean
    private ClientFactory clients;

    @Test
    void failedSecondFetchRollsBackEarlierSnapshotWrites() {
        OrderSnapshot original = orders.selectOne(
                new LambdaQueryWrapper<OrderSnapshot>()
                        .eq(OrderSnapshot::getOrderNo, "ROLLBACK-1"));
        if (original == null) {
            original = new OrderSnapshot();
            original.setOrderNo("ROLLBACK-1");
            original.setWarehouseCode("WH-OLD");
            original.setStatus("CREATED");
            orders.insert(original);
        }

        OrderSnapshot replacement = new OrderSnapshot();
        replacement.setOrderNo("ROLLBACK-1");
        replacement.setWarehouseCode("WH-NEW");
        replacement.setStatus("ALLOCATED");
        OmsClient client = org.mockito.Mockito.mock(OmsClient.class);
        when(clients.oms(any())).thenReturn(client);
        when(client.fetchOrders()).thenReturn(Collections.singletonList(replacement));
        when(client.fetchInventory()).thenThrow(
                new IllegalStateException("inventory fetch failed"));

        assertThrows(RuntimeException.class, () -> worker.syncOne("OMS"));

        OrderSnapshot after = orders.selectOne(
                new LambdaQueryWrapper<OrderSnapshot>()
                        .eq(OrderSnapshot::getOrderNo, "ROLLBACK-1"));
        assertEquals("WH-OLD", after.getWarehouseCode());
        assertEquals("CREATED", after.getStatus());
    }
}
