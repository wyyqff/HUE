package com.unimarket.module.goods.dto;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class GoodsPublishValidationTest {
    @Test
    void rejectsNegativePriceAndDeliveryFee() {
        GoodsPublishDTO dto = new GoodsPublishDTO();
        dto.setTitle("测试商品");
        dto.setCategoryId(1);
        dto.setPrice(new BigDecimal("-10.00"));
        dto.setDeliveryFee(new BigDecimal("-100.00"));
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(dto);
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("price")));
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("deliveryFee")));
        }
    }

    @Test
    void rejectsSubCentPrice() {
        GoodsPublishDTO dto = new GoodsPublishDTO();
        dto.setTitle("测试商品");
        dto.setCategoryId(1);
        dto.setPrice(new BigDecimal("0.001"));
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertFalse(factory.getValidator().validate(dto).isEmpty());
        }
    }
}
