package com.unimarket.module.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unimarket.module.goods.entity.GoodsInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * GoodsInfo Mapper接口
 */
@Mapper
public interface GoodsInfoMapper extends BaseMapper<GoodsInfo> {
    @Select("SELECT * FROM goods_info WHERE product_id = #{productId} AND is_deleted = 0 FOR UPDATE")
    GoodsInfo selectByIdForUpdate(@Param("productId") Long productId);

    @Update("UPDATE goods_info SET collect_count = GREATEST(COALESCE(collect_count, 0) + #{delta}, 0), "
            + "update_time = NOW() WHERE product_id = #{productId} AND is_deleted = 0")
    int adjustCollectCount(@Param("productId") Long productId, @Param("delta") int delta);

    @Update("UPDATE goods_info SET trade_status = 1, update_time = NOW() "
            + "WHERE product_id = #{productId} AND is_deleted = 0 AND trade_status = 0 AND review_status IN (1, 2)")
    int markSoldIfAvailable(@Param("productId") Long productId);
}
