package com.ir.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class UserStore {
    private final UserMapper mapper;

    public UserStore(UserMapper mapper) {
        this.mapper = mapper;
    }

    public User find(String username) {
        return mapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    public User get(Long id) {
        return mapper.selectById(id);
    }

    public User save(User user) {
        if (user.getId() == null) {
            mapper.insert(user);
        } else {
            mapper.updateById(user);
        }
        return user;
    }

    public static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte valueByte : bytes) {
                result.append(String.format("%02x", valueByte));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算密码摘要", ex);
        }
    }
}
