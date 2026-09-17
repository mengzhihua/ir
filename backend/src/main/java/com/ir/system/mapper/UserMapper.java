package com.ir.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.ir.system.entity.User;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
