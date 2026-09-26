package com.unimarket.module.order.service;

import com.unimarket.common.exception.BusinessException;
import com.unimarket.module.dispute.mapper.DisputeRecordMapper;
import com.unimarket.module.goods.entity.GoodsInfo;
import com.unimarket.module.goods.mapper.GoodsInfoMapper;
import com.unimarket.module.notice.service.NoticeService;
import com.unimarket.module.order.dto.RefundApplyDTO;
import com.unimarket.module.order.dto.RefundProcessDTO;
import com.unimarket.module.order.entity.OrderInfo;
import com.unimarket.module.order.mapper.OrderInfoMapper;
import com.unimarket.module.order.service.impl.OrderServiceImpl;
import com.unimarket.module.user.entity.UserInfo;
import com.unimarket.module.user.mapper.UserInfoMapper;
import com.unimarket.module.user.service.CreditScoreService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFinancialSafetyTest {
    @Mock OrderInfoMapper orders;
    @Mock GoodsInfoMapper goods;
    @Mock UserInfoMapper users;
    @Mock NoticeService notices;
    @Mock DisputeRecordMapper disputes;
    @Mock RedissonClient redisson;
    @Mock OrderDelayMessageService delay;
    @Mock CreditScoreService credit;
    @Mock RocketMQTemplate mq;
    @Mock RLock lock;
    @Mock PlatformTransactionManager transactionManager;
    @InjectMocks OrderServiceImpl service;
    OrderInfo order;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(redisson.getLock(anyString())).thenReturn(lock);
        lenient().when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
        order = new OrderInfo();
        order.setOrderId(1L);
        order.setBuyerId(10L);
        order.setSellerId(20L);
        order.setProductId(100L);
        order.setOrderAmount(new BigDecimal("100.00"));
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setOrderStatus(0);
        order.setRefundStatus(0);
        lenient().when(orders.selectByIdForUpdate(1L)).thenReturn(order);
    }

    @Test
    void negativeLegacyOrderCannotCreditBuyerDuringPayment() {
        order.setTotalAmount(new BigDecimal("-90.00"));
        GoodsInfo product = new GoodsInfo();
        product.setTradeStatus(0);
        product.setReviewStatus(1);
        UserInfo buyer = new UserInfo();
        buyer.setMoney(new BigDecimal("100.00"));
        lenient().when(goods.selectByIdForUpdate(100L)).thenReturn(product);
        lenient().when(users.selectById(10L)).thenReturn(buyer);
        assertThrows(BusinessException.class, () -> service.pay(1L));
        verify(users, never()).updateById(any(UserInfo.class));
        assertEquals(new BigDecimal("100.00"), buyer.getMoney());
    }

    @Test
    void partialRefundIsRejectedBeforeMutatingOrder() {
        order.setOrderStatus(2);
        RefundApplyDTO dto = new RefundApplyDTO();
        dto.setAmount(new BigDecimal("20.00"));
        dto.setReason("部分退款");
        assertThrows(BusinessException.class, () -> service.applyRefund(1L, 10L, dto));
        assertEquals(0, order.getRefundStatus());
        verify(orders, never()).updateById(any(OrderInfo.class));
    }

    @Test
    void pendingRefundBlocksDeliveryAndCountdown() {
        order.setOrderStatus(1);
        order.setRefundStatus(1);
        assertThrows(BusinessException.class, () -> service.deliver(1L));
        assertEquals(1, order.getOrderStatus());
        verifyNoInteractions(delay);
        verify(orders, never()).updateById(any(OrderInfo.class));
    }

    @Test
    void unavailableProductDoesNotClaimRolledBackCancellation() {
        GoodsInfo product = new GoodsInfo();
        product.setTradeStatus(1);
        when(goods.selectByIdForUpdate(100L)).thenReturn(product);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.pay(1L));
        assertFalse(ex.getMessage().contains("已自动取消"));
        assertEquals(0, order.getOrderStatus());
        verify(orders, never()).updateById(any(OrderInfo.class));
    }

    @Test
    void deliveredRefundClearsOldAutomaticDeadline() {
        order.setOrderStatus(2);
        order.setRefundDeadline(java.time.LocalDateTime.now().minusDays(1));
        RefundApplyDTO dto = new RefundApplyDTO();
        dto.setReason("申请退货退款");
        dto.setAmount(order.getTotalAmount());
        service.applyRefund(1L, 10L, dto);
        assertNull(order.getRefundDeadline());
        verify(users, never()).creditBalance(any(), any());
    }

    @Test
    void scheduledRefundSkipsDeliveredLegacyRequestsEvenWithExpiredDeadline() {
        order.setOrderStatus(2);
        order.setRefundStatus(1);
        order.setRefundDeadline(java.time.LocalDateTime.now().minusDays(1));
        when(orders.selectList(any())).thenReturn(java.util.List.of(order));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service.autoProcessRefunds();
        verify(users, never()).creditBalance(any(), any());
        verify(orders, never()).updateById(any(OrderInfo.class));
    }

    @Test
    void approvedDeliveredRefundKeepsReturnedItemOffShelf() {
        order.setOrderStatus(2);
        order.setRefundStatus(1);
        order.setRefundAmount(order.getTotalAmount());
        GoodsInfo product = new GoodsInfo();
        product.setProductId(100L);
        product.setReviewStatus(1);
        product.setTradeStatus(1);
        when(goods.selectByIdForUpdate(100L)).thenReturn(product);
        when(users.creditBalance(10L, order.getTotalAmount())).thenReturn(1);
        RefundProcessDTO dto = new RefundProcessDTO();
        dto.setAction("approve");
        service.processRefund(1L, 20L, dto);
        assertEquals(2, product.getTradeStatus());
        assertEquals(4, order.getOrderStatus());
    }
}
