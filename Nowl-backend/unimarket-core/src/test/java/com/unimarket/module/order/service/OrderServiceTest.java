package com.unimarket.module.order.service;

import com.unimarket.common.exception.BusinessException;
import com.unimarket.module.goods.entity.GoodsInfo;
import com.unimarket.module.goods.mapper.GoodsInfoMapper;
import com.unimarket.module.notice.service.NoticeService;
import com.unimarket.module.order.dto.OrderCreateDTO;
import com.unimarket.module.order.entity.OrderInfo;
import com.unimarket.module.order.mapper.OrderInfoMapper;
import com.unimarket.module.order.service.impl.OrderServiceImpl;
import com.unimarket.module.user.entity.UserInfo;
import com.unimarket.module.user.mapper.UserInfoMapper;
import com.unimarket.module.dispute.mapper.DisputeRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderInfoMapper orderInfoMapper;
    @Mock
    private GoodsInfoMapper goodsInfoMapper;
    @Mock
    private UserInfoMapper userInfoMapper;
    @Mock
    private NoticeService noticeService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private OrderDelayMessageService orderDelayMessageService;
    @Mock
    private DisputeRecordMapper disputeRecordMapper;
    @Mock
    private RLock lock;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UserInfo buyer;
    private UserInfo seller;
    private GoodsInfo goods;
    private OrderInfo order;

    @BeforeEach
    void setUp() {
        // 初始化买家
        buyer = new UserInfo();
        buyer.setUserId(1L);
        buyer.setNickName("买家A");
        buyer.setAuthStatus(2); // 已认证
        buyer.setMoney(new BigDecimal("1000.00"));
        buyer.setSchoolCode("HUE");

        // 初始化卖家
        seller = new UserInfo();
        seller.setUserId(2L);
        seller.setNickName("卖家B");
        seller.setMoney(new BigDecimal("500.00"));

        // 初始化商品
        goods = new GoodsInfo();
        goods.setProductId(100L);
        goods.setSellerId(2L);
        goods.setTitle("测试商品");
        goods.setPrice(new BigDecimal("99.00"));
        goods.setDeliveryFee(new BigDecimal("5.00"));
        goods.setTradeStatus(0); // 在售
        goods.setReviewStatus(1);
        goods.setSchoolCode("HUE");
        goods.setTradeType(1);

        // 初始化订单
        order = new OrderInfo();
        order.setOrderId(1000L);
        order.setOrderNo("ORD123456");
        order.setBuyerId(1L);
        order.setSellerId(2L);
        order.setProductId(100L);
        order.setOrderAmount(new BigDecimal("99.00"));
        order.setDeliveryFee(new BigDecimal("5.00"));
        order.setTotalAmount(new BigDecimal("104.00"));
        order.setOrderStatus(0); // 待支付
    }

    @Test
    @DisplayName("创建订单 - 成功场景")
    void create_success() throws InterruptedException {
        // Arrange
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(userInfoMapper.selectById(1L)).thenReturn(buyer);
        when(goodsInfoMapper.selectByIdForUpdate(100L)).thenReturn(goods);
        when(orderInfoMapper.insert(any(OrderInfo.class))).thenReturn(1);

        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setProductId(100L);
        dto.setRemark("测试备注");

        // Act & Assert
        assertDoesNotThrow(() -> orderService.create(1L, dto));
        verify(orderInfoMapper).insert(any(OrderInfo.class));
        verify(noticeService).sendNotice(eq(2L), anyString(), anyString(), eq(1));
    }

    @Test
    @DisplayName("创建订单 - 实名校验已上移到鉴权层，Service不再拦截")
    void create_unverifiedUser_allowedInService() throws InterruptedException {
        // Arrange
        buyer.setAuthStatus(0); // 未认证
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(userInfoMapper.selectById(1L)).thenReturn(buyer);
        when(goodsInfoMapper.selectByIdForUpdate(100L)).thenReturn(goods);
        when(orderInfoMapper.insert(any(OrderInfo.class))).thenReturn(1);

        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setProductId(100L);

        // Act & Assert
        assertDoesNotThrow(() -> orderService.create(1L, dto));
        verify(orderInfoMapper).insert(any(OrderInfo.class));
    }

    @Test
    @DisplayName("支付订单 - 成功场景")
    void pay_success() throws InterruptedException {
        // Arrange
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(orderInfoMapper.selectByIdForUpdate(1000L)).thenReturn(order);
        when(userInfoMapper.debitBalance(1L, new BigDecimal("104.00"))).thenReturn(1);
        when(goodsInfoMapper.selectByIdForUpdate(100L)).thenReturn(goods);
        when(goodsInfoMapper.markSoldIfAvailable(100L)).thenReturn(1);

        // Act
        assertDoesNotThrow(() -> orderService.pay(1000L));

        // Assert
        verify(userInfoMapper).debitBalance(1L, new BigDecimal("104.00"));
        verify(orderInfoMapper).updateById(order);
        verify(userInfoMapper, never()).updateById(any(UserInfo.class));
        assertEquals(1, order.getOrderStatus()); // 待发货
    }

    @Test
    @DisplayName("支付订单 - 余额不足抛出异常")
    void pay_insufficientBalance_throwsException() throws InterruptedException {
        // Arrange
        buyer.setMoney(new BigDecimal("50.00")); // 余额不足
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(orderInfoMapper.selectByIdForUpdate(1000L)).thenReturn(order);
        when(goodsInfoMapper.selectByIdForUpdate(100L)).thenReturn(goods);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.pay(1000L));
        assertTrue(ex.getMessage().contains("余额不足"));
    }

    @Test
    @DisplayName("支付订单 - 订单状态不正确抛出异常")
    void pay_invalidOrderStatus_throwsException() throws InterruptedException {
        // Arrange
        order.setOrderStatus(1); // 已支付
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(orderInfoMapper.selectByIdForUpdate(1000L)).thenReturn(order);

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.pay(1000L));
        assertTrue(ex.getMessage().contains("订单状态不正确"));
    }

    @Test
    @DisplayName("确认收货 - 成功场景")
    void confirm_success() throws InterruptedException {
        // Arrange
        order.setOrderStatus(2); // 待收货
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(orderInfoMapper.selectByIdForUpdate(1000L)).thenReturn(order);
        when(userInfoMapper.creditBalance(2L, new BigDecimal("104.00"))).thenReturn(1);

        // Act
        assertDoesNotThrow(() -> orderService.confirm(1000L));

        // Assert
        verify(userInfoMapper).creditBalance(2L, new BigDecimal("104.00"));
        verify(userInfoMapper, never()).updateById(any(UserInfo.class));
        assertEquals(3, order.getOrderStatus()); // 已完成
    }

    @Test
    void create_faceToFaceOmitsPostageAndSavesSelectedMethod() throws Exception {
        prepareCreate();
        goods.setTradeType(2);
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setProductId(100L);
        dto.setTradeType(0);
        orderService.create(1L, dto);
        var saved = org.mockito.ArgumentCaptor.forClass(OrderInfo.class);
        verify(orderInfoMapper).insert(saved.capture());
        assertEquals(0, saved.getValue().getTradeType());
        assertEquals(0, saved.getValue().getDeliveryFee().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("99.00"), saved.getValue().getTotalAmount());
    }

    @Test
    void create_mixedDeliveryRequiresExplicitChoice() throws Exception {
        prepareCreate();
        goods.setTradeType(2);
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setProductId(100L);
        assertThrows(BusinessException.class, () -> orderService.create(1L, dto));
        verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
    }

    @Test
    void create_rejectsUnsupportedMethod() throws Exception {
        prepareCreate();
        goods.setTradeType(0);
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setProductId(100L);
        dto.setTradeType(1);
        assertThrows(BusinessException.class, () -> orderService.create(1L, dto));
        verify(orderInfoMapper, never()).insert(any(OrderInfo.class));
    }

    private void prepareCreate() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(userInfoMapper.selectById(1L)).thenReturn(buyer);
        when(goodsInfoMapper.selectByIdForUpdate(100L)).thenReturn(goods);
        lenient().when(orderInfoMapper.insert(any(OrderInfo.class))).thenReturn(1);
    }
}
