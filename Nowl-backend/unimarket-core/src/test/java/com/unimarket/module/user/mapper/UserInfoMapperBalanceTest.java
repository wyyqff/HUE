package com.unimarket.module.user.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.unimarket.module.user.entity.UserInfo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserInfoMapperBalanceTest {
    @Test
    void debitUsesPositiveAmountAndSufficientFundsConditions() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(UserInfoMapper.class);
        String statementId = UserInfoMapper.class.getName() + ".debitBalance";
        assertTrue(configuration.hasStatement(statementId), "扣款必须由数据库原子执行");
        String sql = configuration.getMappedStatement(statementId)
                .getBoundSql(Map.of("userId", 1L, "amount", new BigDecimal("80.00"))).getSql()
                .replaceAll("\\s+", " ").toLowerCase();
        assertTrue(sql.contains("money = money - ?"));
        assertTrue(sql.contains("? > 0"));
        assertTrue(sql.contains("money >= ?"));
        assertTrue(sql.contains("where user_id = ?"));
    }

    @Test
    void creditAddsOnlyPositiveAmountToCurrentDatabaseBalance() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(UserInfoMapper.class);
        String statementId = UserInfoMapper.class.getName() + ".creditBalance";
        assertTrue(configuration.hasStatement(statementId), "入账必须由数据库原子执行");
        String sql = configuration.getMappedStatement(statementId)
                .getBoundSql(Map.of("userId", 1L, "amount", new BigDecimal("80.00"))).getSql()
                .replaceAll("\\s+", " ").toLowerCase();
        assertTrue(sql.contains("money = money + ?"));
        assertTrue(sql.contains("? > 0"));
        assertTrue(sql.contains("where user_id = ?"));
    }

    @Test
    void updatingProfileDoesNotOverwriteBalanceReadBeforeConcurrentPayment() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(UserInfoMapper.class);
        UserInfo staleProfile = new UserInfo();
        staleProfile.setUserId(1L);
        staleProfile.setNickName("新昵称");
        staleProfile.setMoney(new BigDecimal("100.00"));

        String sql = configuration.getMappedStatement(UserInfoMapper.class.getName() + ".updateById")
                .getBoundSql(Map.of("et", staleProfile)).getSql();

        assertTrue(sql.contains("nick_name"));
        assertFalse(sql.contains("money"), "普通资料更新不能写回并发交易前的旧余额");
    }
}
