package com.ir.cost;

import com.ir.common.R;
import com.ir.snapshot.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/cost")
public class CostController {
    private final DataStore store;private final List<Map<String,Object>> targets=new ArrayList<>();
    public CostController(DataStore store){this.store=store;}
    @GetMapping("/summary") public R<Map<String,Object>> summary(@RequestParam(defaultValue="30")int days){LocalDate from=LocalDate.now().minusDays(days-1);BigDecimal total=BigDecimal.ZERO;Map<String,BigDecimal>type=new LinkedHashMap<>(),wh=new LinkedHashMap<>(),carrier=new LinkedHashMap<>();List<Map<String,Object>>trend=new ArrayList<>();for(CostRecord c:store.costs)if(!c.getBizDate().isBefore(from)){total=total.add(c.getAmount());add(type,c.getCostType(),c.getAmount());add(wh,c.getWarehouseCode(),c.getAmount());add(carrier,c.getCarrierCode()==null?"":c.getCarrierCode(),c.getAmount());}Map<String,Object>m=new LinkedHashMap<>();m.put("total",total);m.put("byType",type);m.put("byWarehouse",wh);m.put("byCarrier",carrier);m.put("costPerOrder",store.orders.isEmpty()?BigDecimal.ZERO:total.divide(BigDecimal.valueOf(store.orders.size()),2,BigDecimal.ROUND_HALF_UP));m.put("trend",trend);m.put("targetVsActual",Collections.emptyMap());return R.ok(m);}
    @GetMapping("/page") public R<List<CostRecord>> page(@RequestParam(required=false)String orderNo,@RequestParam(required=false)String costType,@RequestParam(required=false)String warehouseCode,@RequestParam(required=false)String carrierCode){List<CostRecord>o=new ArrayList<>();for(CostRecord c:store.costs)if((orderNo==null||orderNo.equals(c.getOrderNo()))&&(costType==null||costType.equals(c.getCostType()))&&(warehouseCode==null||warehouseCode.equals(c.getWarehouseCode()))&&(carrierCode==null||carrierCode.equals(c.getCarrierCode())))o.add(c);return R.ok(o);}
    @GetMapping("/saving") public R<Map<String,Object>> saving(){Map<String,Object>m=new LinkedHashMap<>();m.put("total",BigDecimal.ZERO);m.put("byMonth",Collections.emptyMap());return R.ok(m);}
    @GetMapping("/target") public R<List<Map<String,Object>>> target(){return R.ok(targets);}
    @PostMapping("/target") public R<Map<String,Object>> target(@RequestBody Map<String,Object>body){targets.add(body);return R.ok(body);}
    private void add(Map<String,BigDecimal>m,String k,BigDecimal v){m.put(k,m.getOrDefault(k,BigDecimal.ZERO).add(v));}
}
