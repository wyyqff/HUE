package com.unimarket.module.order.dto;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Range;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 订单创建DTO
 */
@Data
public class OrderCreateDTO {

    /**
     * 商品ID
     */
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** 实际交付方式：0-面交，1-邮寄；皆可商品必须明确选择。 */
    @Range(min = 0, max = 1, message = "请选择面交或邮寄")
    private Integer tradeType;

    /**
     * 订单备注
     */
    @Length(max = 200, message = "订单备注不能超过200个字符")
    private String remark;
}
