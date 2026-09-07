package com.ir.alert;

import com.ir.action.ActionService;
import com.ir.snapshot.DataStore;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertEngineTest {
    @Test void stuckOrderRuleFiresAndIsIdempotent(){DataStore store=new DataStore();store.init();AlertEngine e=new AlertEngine(store,mock(ActionService.class));int first=e.evaluate().size();int second=e.evaluate().size();assertTrue(first>0);assertEquals(first,second);}
}
