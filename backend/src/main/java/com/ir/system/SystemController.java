package com.ir.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ir.common.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final UserMapper userMapper;
    private final OpLogMapper opLogMapper;

    public SystemController(UserMapper userMapper, OpLogMapper opLogMapper) {
        this.userMapper = userMapper;
        this.opLogMapper = opLogMapper;
    }

    @GetMapping("/user")
    public R<Page<User>> users(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<>();
        if (role != null) {
            query.eq(User::getRole, role);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            query.and(wrapper -> wrapper
                    .like(User::getUsername, keyword.trim())
                    .or()
                    .like(User::getRealName, keyword.trim()));
        }
        query.orderByAsc(User::getId);
        Page<User> result = userMapper.selectPage(
                new Page<>(current, size), query);
        result.getRecords().forEach(user -> user.setPassword(null));
        return R.ok(result);
    }

    @PostMapping("/user")
    public R<User> create(@RequestBody User user) {
        user.setPassword(UserStore.hash(user.getPassword()));
        if (user.getEnabled() == null) {
            user.setEnabled(true);
        }
        userMapper.insert(user);
        user.setPassword(null);
        return R.ok(user);
    }

    @PutMapping("/user/{id}")
    public R<User> update(@PathVariable Long id, @RequestBody User request) {
        User existing = userMapper.selectById(id);
        if (existing == null) {
            return R.fail(404, "用户不存在");
        }
        request.setId(id);
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            request.setPassword(existing.getPassword());
        } else {
            request.setPassword(UserStore.hash(request.getPassword()));
        }
        userMapper.updateById(request);
        User result = userMapper.selectById(id);
        result.setPassword(null);
        return R.ok(result);
    }

    @DeleteMapping("/user/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userMapper.deleteById(id);
        return R.ok();
    }

    @GetMapping("/op-log/page")
    public R<Page<OpLog>> logs(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        LambdaQueryWrapper<OpLog> query = new LambdaQueryWrapper<>();
        if (operator != null && !operator.trim().isEmpty()) {
            query.eq(OpLog::getOperator, operator);
        }
        if (module != null && !module.trim().isEmpty()) {
            query.eq(OpLog::getModule, module);
        }
        if (action != null && !action.trim().isEmpty()) {
            query.like(OpLog::getAction, action.trim());
        }
        query.orderByDesc(OpLog::getCreatedAt);
        return R.ok(opLogMapper.selectPage(
                new Page<>(current, size), query));
    }
}
