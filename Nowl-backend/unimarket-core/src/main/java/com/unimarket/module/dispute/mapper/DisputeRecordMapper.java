package com.unimarket.module.dispute.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unimarket.module.dispute.entity.DisputeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 纠纷记录Mapper
 */
@Mapper
public interface DisputeRecordMapper extends BaseMapper<DisputeRecord> {
    @Select("SELECT * FROM dispute_record WHERE record_id = #{recordId} FOR UPDATE")
    DisputeRecord selectByIdForUpdate(@Param("recordId") Long recordId);
}
