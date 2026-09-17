package com.ir.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.ir.system.entity.OpLog;

@Mapper
public interface OpLogMapper extends BaseMapper<OpLog> {
}
