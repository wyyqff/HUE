package com.unimarket.common.utils;

import com.unimarket.common.exception.BusinessException;
import java.math.BigDecimal;

/** Validates monetary values before they enter balance or order calculations. */
public final class MoneyValidator {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    private MoneyValidator() { }

    public static void requireValid(BigDecimal amount, boolean allowZero, String label) {
        if (amount == null || amount.signum() < 0 || (!allowZero && amount.signum() == 0)
                || amount.stripTrailingZeros().scale() > 2 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(label + "不合法，请使用有效的两位小数金额");
        }
    }
}
