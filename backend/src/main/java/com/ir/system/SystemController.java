package com.ir.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.util.List;

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
    public R<List<User>> users(@RequestParam(required = false) String role) {
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<>();
        if (role != null) {
            query.eq(User::getRole, role);
        }
        query.orderByAsc(User::getId);
        return R.ok(userMapper.selectList(query));
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
    public R<List<OpLog>> logs() {
        return R.ok(opLogMapper.selectList(
                new LambdaQueryWrapper<OpLog>()
                        .orderByDesc(OpLog::getCreatedAt)));
    }
}
