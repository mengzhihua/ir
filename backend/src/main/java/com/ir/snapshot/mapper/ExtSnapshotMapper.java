package com.ir.snapshot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.ir.snapshot.entity.ExtSnapshot;
import java.util.List;
import java.util.Map;

@Mapper
public interface ExtSnapshotMapper extends BaseMapper<ExtSnapshot> {
    @Select("SELECT source_system AS sourceSystem, data_type AS dataType, status, COUNT(*) AS cnt "
            + "FROM ct_ext_snapshot GROUP BY source_system, data_type, status "
            + "ORDER BY source_system, data_type, status")
    List<Map<String, Object>> counts();
}
