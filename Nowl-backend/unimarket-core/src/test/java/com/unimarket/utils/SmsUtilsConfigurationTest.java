package com.unimarket.utils;

import com.unimarket.common.config.SmsProperties;
import com.unimarket.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmsUtilsConfigurationTest {

    @Test
    void disabledSmsMustNotPretendDeliverySucceeded() {
        SmsProperties properties = new SmsProperties();
        properties.setEnabled(false);
        SmsUtils smsUtils = new SmsUtils(properties);

        BusinessException error = assertThrows(BusinessException.class,
                () -> smsUtils.sendSmsCode("13800000000", "123456"));

        assertEquals("短信服务尚未配置，请联系管理员", error.getMessage());
    }

    @Test
    void missingProviderUrlIsReportedBeforeSending() {
        SmsProperties properties = new SmsProperties();
        properties.setEnabled(true);
        properties.setUrl("   ");
        SmsUtils smsUtils = new SmsUtils(properties);

        BusinessException error = assertThrows(BusinessException.class,
                () -> smsUtils.sendSmsCode("13800000000", "123456"));

        assertEquals("短信服务尚未配置，请联系管理员", error.getMessage());
    }
}
