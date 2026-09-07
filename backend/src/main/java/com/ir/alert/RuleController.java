package com.ir.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ir.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rule")
public class RuleController {
    private final CtRuleMapper mapper;

    public RuleController(CtRuleMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/page")
    public R<Page<CtRule>> page(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1")
            long current,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20")
            long size) {
        return R.ok(mapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<CtRule>()
                        .orderByAsc(CtRule::getId)));
    }

    @PutMapping("/{id}")
    public R<CtRule> update(
            @PathVariable Long id,
            @RequestBody CtRule request) {
        request.setId(id);
        mapper.updateById(request);
        return R.ok(mapper.selectById(id));
    }
}
