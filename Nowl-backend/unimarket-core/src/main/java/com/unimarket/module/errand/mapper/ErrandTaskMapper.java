package com.unimarket.module.errand.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unimarket.module.errand.entity.ErrandTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * ErrandTask Mapper接口
 */
@Mapper
public interface ErrandTaskMapper extends BaseMapper<ErrandTask> {
    @Select("SELECT * FROM errand_task WHERE task_id = #{taskId} FOR UPDATE")
    ErrandTask selectByIdForUpdate(@Param("taskId") Long taskId);
}
