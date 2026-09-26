package com.unimarket.module.user.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.unimarket.module.user.entity.UserInfo;
import com.unimarket.module.errand.entity.ErrandTask;
import com.unimarket.module.errand.mapper.ErrandTaskMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 使用独立 H2 内存库及真实 Mapper SQL；不启动 Spring，不读取应用数据源配置。 */
class UserInfoMapperBalanceConcurrencyTest {
    private Connection keeper;
    private SqlSessionFactory sessions;

    @BeforeEach
    void createMemoryDatabase() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:balance_" + UUID.randomUUID() + ";MODE=MySQL");
        keeper = source.getConnection();
        keeper.createStatement().execute("CREATE TABLE user_info (user_id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "money DECIMAL(10,2) NOT NULL DEFAULT 0, nick_name VARCHAR(100), "
                + "create_time TIMESTAMP, update_time TIMESTAMP)");
        keeper.createStatement().execute("INSERT INTO user_info(user_id, money, nick_name) VALUES (1, 100.00, '旧昵称')");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("memory-only", new JdbcTransactionFactory(), source));
        configuration.addMapper(UserInfoMapper.class);
        configuration.addMapper(ErrandTaskMapper.class);
        sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void closeMemoryDatabase() throws Exception {
        keeper.close();
    }

    @Test
    void concurrentPaymentsCanDebitOnlyOneOfTwoUnaffordableOrders() throws Exception {
        int affected = concurrently(mapper -> mapper.debitBalance(1L, new BigDecimal("80.00")));
        assertEquals(1, affected);
        assertEquals(new BigDecimal("20.00"), balance(1L));
    }

    @Test
    void concurrentCreditsPreserveEveryPayment() throws Exception {
        int affected = concurrently(mapper -> {
            int updated = 0;
            for (int i = 0; i < 30; i++) {
                updated += mapper.creditBalance(1L, new BigDecimal("1.00"));
            }
            return updated;
        });
        assertEquals(60, affected);
        assertEquals(new BigDecimal("160.00"), balance(1L));
    }

    @Test
    void rejectsNonpositiveAmountsMissingAccountsAndInsufficientFunds() throws Exception {
        try (SqlSession session = sessions.openSession(true)) {
            UserInfoMapper mapper = session.getMapper(UserInfoMapper.class);
            assertEquals(0, mapper.debitBalance(1L, new BigDecimal("-10.00")));
            assertEquals(0, mapper.creditBalance(1L, new BigDecimal("-10.00")));
            assertEquals(0, mapper.debitBalance(1L, BigDecimal.ZERO));
            assertEquals(0, mapper.creditBalance(1L, BigDecimal.ZERO));
            assertEquals(0, mapper.debitBalance(1L, new BigDecimal("100.01")));
            assertEquals(0, mapper.debitBalance(99L, BigDecimal.ONE));
            assertEquals(0, mapper.creditBalance(99L, BigDecimal.ONE));
        }
        assertEquals(new BigDecimal("100.00"), balance(1L));
    }

    @Test
    void rejectsFractionalCentCreditWithoutRoundingMoney() throws Exception {
        try (SqlSession session = sessions.openSession(true)) {
            assertEquals(0, session.getMapper(UserInfoMapper.class).creditBalance(1L, new BigDecimal("0.005")));
        }
        assertEquals(new BigDecimal("100.00"), balance(1L));
    }

    @Test
    void rejectsFractionalCentDebitWithoutRoundingMoney() throws Exception {
        try (SqlSession session = sessions.openSession(true)) {
            assertEquals(0, session.getMapper(UserInfoMapper.class).debitBalance(1L, new BigDecimal("0.005")));
        }
        assertEquals(new BigDecimal("100.00"), balance(1L));
    }

    @Test
    void staleProfileUpdatePreservesConcurrentDebit() throws Exception {
        UserInfo staleProfile = new UserInfo();
        staleProfile.setUserId(1L);
        staleProfile.setMoney(new BigDecimal("100.00"));
        staleProfile.setNickName("新昵称");
        try (SqlSession session = sessions.openSession(true)) {
            UserInfoMapper mapper = session.getMapper(UserInfoMapper.class);
            assertEquals(1, mapper.debitBalance(1L, new BigDecimal("80.00")));
            assertEquals(1, mapper.updateById(staleProfile));
        }
        assertEquals(new BigDecimal("20.00"), balance(1L));
        try (ResultSet row = keeper.createStatement().executeQuery("SELECT nick_name FROM user_info WHERE user_id = 1")) {
            row.next();
            assertEquals("新昵称", row.getString(1));
        }
    }

    @Test
    void registeringUserStillInsertsInitialBalance() throws Exception {
        UserInfo user = new UserInfo();
        user.setUserId(2L);
        user.setMoney(new BigDecimal("50.00"));
        try (SqlSession session = sessions.openSession(true)) {
            assertEquals(1, session.getMapper(UserInfoMapper.class).insert(user));
        }
        assertEquals(new BigDecimal("50.00"), balance(2L));
    }

    @Test
    void errandSettlementLockSurvivesRedisReleaseAndPreventsDuplicateCredit() throws Exception {
        keeper.createStatement().execute("CREATE TABLE errand_task (task_id BIGINT PRIMARY KEY, task_status INT)");
        keeper.createStatement().execute("INSERT INTO errand_task VALUES (1, 2)");
        String statement = ErrandTaskMapper.class.getName() + ".selectByIdForUpdate";
        assertTrue(sessions.getConfiguration().hasStatement(statement), "跑腿结算必须锁定任务直到事务提交");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (SqlSession first = sessions.openSession(false)) {
            ErrandTask task = first.selectOne(statement, Map.of("taskId", 1L));
            assertEquals(2, task.getTaskStatus());
            assertEquals(1, first.getMapper(UserInfoMapper.class).creditBalance(1L, new BigDecimal("12.50")));
            first.getConnection().createStatement().executeUpdate("UPDATE errand_task SET task_status = 3 WHERE task_id = 1");
            CountDownLatch started = new CountDownLatch(1);
            Future<Integer> next = pool.submit(() -> {
                try (SqlSession second = sessions.openSession(false)) {
                    started.countDown();
                    ErrandTask current = second.selectOne(statement, Map.of("taskId", 1L));
                    int credited = current.getTaskStatus() == 2
                            ? second.getMapper(UserInfoMapper.class).creditBalance(1L, new BigDecimal("12.50")) : 0;
                    second.commit(true);
                    return credited;
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> next.get(200, TimeUnit.MILLISECONDS));
            first.commit(true);
            assertEquals(0, next.get(5, TimeUnit.SECONDS));
            assertEquals(new BigDecimal("112.50"), balance(1L));
        } finally {
            pool.shutdownNow();
        }
    }

    private int concurrently(ToIntFunction<UserInfoMapper> operation) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        java.util.concurrent.Callable<Integer> action = () -> {
            try (SqlSession session = sessions.openSession(true)) {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("并发测试启动超时");
                }
                return operation.applyAsInt(session.getMapper(UserInfoMapper.class));
            }
        };
        try {
            Future<Integer> first = pool.submit(action);
            Future<Integer> second = pool.submit(action);
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发测试连接准备超时");
            }
            start.countDown();
            return first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private BigDecimal balance(Long userId) throws Exception {
        try (var statement = keeper.prepareStatement("SELECT money FROM user_info WHERE user_id = ?")) {
            statement.setLong(1, userId);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getBigDecimal(1);
            }
        }
    }
}
