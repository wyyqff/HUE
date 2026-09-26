package com.unimarket.module.dispute.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.unimarket.module.dispute.entity.DisputeRecord;
import com.unimarket.module.dispute.mapper.DisputeRecordMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class DisputeRecordLockTest {
    @Test
    void secondHandlerWaitsForCommitAndReadsTerminalStatus() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:dispute_" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        try (var keeper = source.getConnection()) {
            keeper.createStatement().execute("CREATE TABLE dispute_record (record_id BIGINT PRIMARY KEY, handle_status INT)");
            keeper.createStatement().execute("INSERT INTO dispute_record VALUES (1, 0)");
            var configuration = new MybatisConfiguration();
            configuration.setEnvironment(new Environment("memory-only", new JdbcTransactionFactory(), source));
            configuration.addMapper(DisputeRecordMapper.class);
            String statement = DisputeRecordMapper.class.getName() + ".selectByIdForUpdate";
            assertTrue(configuration.hasStatement(statement), "纠纷处理必须先锁定记录并读取最新状态");
            var sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
            var pool = Executors.newSingleThreadExecutor();
            try (var first = sessions.openSession(false)) {
                DisputeRecord pending = first.selectOne(statement, Map.of("recordId", 1L));
                assertEquals(0, pending.getHandleStatus());
                first.getConnection().createStatement().executeUpdate("UPDATE dispute_record SET handle_status = 2 WHERE record_id = 1");
                var started = new CountDownLatch(1);
                var next = pool.submit(() -> {
                    try (var second = sessions.openSession(false)) {
                        started.countDown();
                        DisputeRecord record = second.selectOne(statement, Map.of("recordId", 1L));
                        second.commit(true);
                        return record.getHandleStatus();
                    }
                });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> next.get(200, TimeUnit.MILLISECONDS));
                first.commit(true);
                assertEquals(2, next.get(5, TimeUnit.SECONDS));
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
