package com.unimarket.module.order.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.unimarket.module.goods.mapper.GoodsInfoMapper;
import com.unimarket.module.goods.mapper.CollectionRecordMapper;
import com.unimarket.module.goods.entity.GoodsInfo;
import com.unimarket.module.goods.entity.CollectionRecord;
import com.unimarket.module.goods.service.impl.GoodsServiceImpl;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import com.unimarket.module.order.mapper.OrderInfoMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real mapper SQL in an isolated in-memory database; no application configuration is loaded. */
class TradeStateConcurrencyTest {
    private Connection keeper;
    private SqlSessionFactory sessions;

    @BeforeEach
    void setUp() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:trade_" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=5000");
        keeper = source.getConnection();
        keeper.createStatement().execute("CREATE TABLE goods_info (product_id BIGINT PRIMARY KEY, "
                + "trade_status INT, review_status INT, collect_count INT, is_deleted INT, update_time TIMESTAMP)");
        keeper.createStatement().execute("INSERT INTO goods_info VALUES (1, 0, 1, 0, 0, CURRENT_TIMESTAMP)");
        keeper.createStatement().execute("CREATE TABLE order_info (order_id BIGINT PRIMARY KEY, order_status INT)");
        keeper.createStatement().execute("INSERT INTO order_info VALUES (1, 0)");
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("memory-only", new JdbcTransactionFactory(), source));
        configuration.addMapper(GoodsInfoMapper.class);
        configuration.addMapper(OrderInfoMapper.class);
        sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void close() throws Exception { keeper.close(); }

    @Test
    void onlyOnePaymentCanSellTheSameProduct() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Integer> pay = () -> {
            try (var session = sessions.openSession(true)) {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return session.getMapper(GoodsInfoMapper.class).markSoldIfAvailable(1L);
            }
        };
        try {
            var first = pool.submit(pay);
            var second = pool.submit(pay);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, first.get(5, TimeUnit.SECONDS) + second.get(5, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void collectionAfterPaymentKeepsProductSold() {
        try (var session = sessions.openSession(true)) {
            var goods = session.getMapper(GoodsInfoMapper.class);
            assertEquals(1, goods.markSoldIfAvailable(1L));
            assertEquals(1, goods.adjustCollectCount(1L, 1));
            assertEquals(1, goods.selectByIdForUpdate(1L).getTradeStatus());
            assertEquals(1, goods.selectByIdForUpdate(1L).getCollectCount());
            assertEquals(0, goods.markSoldIfAvailable(1L));
        }
    }

    @Test
    void collectionServiceDoesNotRestoreTheOnSaleSnapshotReadBeforePayment() {
        try (var session = sessions.openSession(true)) {
            var databaseGoods = session.getMapper(GoodsInfoMapper.class);
            var serviceGoods = mock(GoodsInfoMapper.class);
            var collections = mock(CollectionRecordMapper.class);
            when(serviceGoods.selectById(1L)).thenAnswer(invocation -> databaseGoods.selectByIdForUpdate(1L));
            when(collections.insert(any(CollectionRecord.class))).thenAnswer(invocation -> {
                assertEquals(1, databaseGoods.markSoldIfAvailable(1L));
                return 1;
            });
            lenient().when(serviceGoods.updateById(any(GoodsInfo.class)))
                    .thenAnswer(invocation -> databaseGoods.updateById(invocation.getArgument(0, GoodsInfo.class)));
            lenient().when(serviceGoods.adjustCollectCount(anyLong(), anyInt()))
                    .thenAnswer(invocation -> databaseGoods.adjustCollectCount(invocation.getArgument(0), invocation.getArgument(1)));
            var service = new GoodsServiceImpl(serviceGoods, collections, null, null, null, null,
                    null, mock(RocketMQTemplate.class), null, null);
            service.collect(10L, 1L);
            session.clearCache();
            assertEquals(1, databaseGoods.selectByIdForUpdate(1L).getTradeStatus());
            assertEquals(1, databaseGoods.selectByIdForUpdate(1L).getCollectCount());
        }
    }

    @Test
    void orderLockIsHeldUntilCommitAndNextReaderSeesCommittedState() throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        try (var first = sessions.openSession(false)) {
            assertEquals(0, first.getMapper(OrderInfoMapper.class).selectByIdForUpdate(1L).getOrderStatus());
            first.getConnection().createStatement().executeUpdate("UPDATE order_info SET order_status = 3 WHERE order_id = 1");
            var started = new CountDownLatch(1);
            var next = pool.submit(() -> {
                try (var second = sessions.openSession(false)) {
                    started.countDown();
                    int status = second.getMapper(OrderInfoMapper.class).selectByIdForUpdate(1L).getOrderStatus();
                    second.commit(true);
                    return status;
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> next.get(200, TimeUnit.MILLISECONDS));
            first.commit(true);
            assertEquals(3, next.get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
