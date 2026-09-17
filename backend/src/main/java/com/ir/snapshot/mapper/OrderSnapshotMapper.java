package com.ir.snapshot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.ir.snapshot.entity.OrderSnapshot;

@Mapper
public interface OrderSnapshotMapper extends BaseMapper<OrderSnapshot> {
}
