package com.unimarket.admin.service;

import com.unimarket.admin.service.impl.domain.AdminDisputeDomainService;
import com.unimarket.admin.service.impl.support.AdminActionLockSupport;
import com.unimarket.admin.service.impl.support.AdminScopeSupport;
import com.unimarket.admin.service.impl.support.AdminSchoolInfoSupport;
import com.unimarket.common.enums.DisputeTargetType;
import com.unimarket.common.enums.DisputeStatus;
import com.unimarket.common.enums.ErrandStatus;
import com.unimarket.common.enums.NoticeType;
import com.unimarket.common.enums.OrderStatus;
import com.unimarket.common.exception.BusinessException;
import com.unimarket.module.errand.entity.ErrandTask;
import com.unimarket.module.errand.mapper.ErrandTaskMapper;
import com.unimarket.module.goods.mapper.GoodsInfoMapper;
import com.unimarket.module.goods.entity.GoodsInfo;
import com.unimarket.module.dispute.entity.DisputeRecord;
import com.unimarket.module.dispute.mapper.DisputeRecordMapper;
import com.unimarket.module.iam.service.IamAccessService;
import com.unimarket.module.notice.service.NoticeService;
import com.unimarket.module.order.mapper.OrderInfoMapper;
import com.unimarket.module.order.entity.OrderInfo;
import com.unimarket.module.user.entity.UserInfo;
import com.unimarket.module.user.mapper.UserInfoMapper;
import com.unimarket.module.user.service.CreditScoreService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDisputeDomainServiceTest {

    @Mock
    private DisputeRecordMapper disputeRecordMapper;
    @Mock
    private OrderInfoMapper orderInfoMapper;
    @Mock
    private ErrandTaskMapper errandTaskMapper;
    @Mock
    private UserInfoMapper userInfoMapper;
    @Mock
    private GoodsInfoMapper goodsInfoMapper;
    @Mock
    private CreditScoreService creditScoreService;
    @Mock
    private NoticeService noticeService;
    @Mock
    private IamAccessService iamAccessService;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private AdminActionLockSupport actionLockSupport;
    @Mock
    private AdminScopeSupport scopeSupport;
    @Mock
    private AdminSchoolInfoSupport schoolInfoSupport;

    @InjectMocks
    private AdminDisputeDomainService disputeDomainService;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(actionLockSupport).withLock(anyString(), any(Runnable.class));
    }

    @Test
    @DisplayName("handleDispute: 处理完成后通知双方并携带纠纷记录ID作为relatedId")
    void handleDispute_sendNoticeWithRecordId() {
        DisputeRecord record = new DisputeRecord();
        record.setRecordId(10L);
        record.setInitiatorId(100L);
        record.setRelatedId(200L);
        record.setSchoolCode("SC001");
        record.setCampusCode("C001");
        record.setHandleStatus(DisputeStatus.PENDING.getCode());

        when(disputeRecordMapper.selectByIdForUpdate(10L)).thenReturn(record);
        when(disputeRecordMapper.updateById(any(DisputeRecord.class))).thenReturn(1);

        disputeDomainService.handleDispute(
                1L,
                10L,
                "同意退款",
                2,
                null,
                null
        );

        verify(iamAccessService).assertCanManageScope(1L, "SC001", "C001");
        verify(noticeService).sendNotice(
                100L,
                "纠纷处理结果",
                "您发起的纠纷已处理，结果：纠纷已处理；处理说明：同意退款",
                NoticeType.DISPUTE.getCode(),
                10L
        );
        verify(noticeService).sendNotice(
                200L,
                "纠纷处理结果",
                "涉及您的纠纷已处理，结果：纠纷已处理；处理说明：同意退款",
                NoticeType.DISPUTE.getCode(),
                10L
        );
    }

    @Test
    @DisplayName("handleDispute: 已终态且请求一致时按幂等忽略")
    void handleDispute_terminalSameRequest_idempotentIgnore() {
        DisputeRecord record = new DisputeRecord();
        record.setRecordId(10L);
        record.setInitiatorId(100L);
        record.setRelatedId(200L);
        record.setSchoolCode("SC001");
        record.setCampusCode("C001");
        record.setHandleStatus(DisputeStatus.RESOLVED.getCode());
        record.setHandleResult("同意退款");
        when(disputeRecordMapper.selectByIdForUpdate(10L)).thenReturn(record);

        disputeDomainService.handleDispute(
                1L,
                10L,
                "同意退款",
                DisputeStatus.RESOLVED.getCode(),
                null,
                null
        );

        verify(disputeRecordMapper, never()).updateById(any(DisputeRecord.class));
        verify(noticeService, never()).sendNotice(anyLong(), anyString(), anyString(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("handleDispute: 已终态且请求冲突时抛出异常")
    void handleDispute_terminalConflict_throw() {
        DisputeRecord record = new DisputeRecord();
        record.setRecordId(10L);
        record.setInitiatorId(100L);
        record.setRelatedId(200L);
        record.setSchoolCode("SC001");
        record.setCampusCode("C001");
        record.setHandleStatus(DisputeStatus.RESOLVED.getCode());
        record.setHandleResult("同意退款");
        when(disputeRecordMapper.selectByIdForUpdate(10L)).thenReturn(record);

        assertThrows(BusinessException.class, () -> disputeDomainService.handleDispute(
                1L,
                10L,
                "拒绝退款",
                DisputeStatus.REJECTED.getCode(),
                null,
                null
        ));
    }

    @Test
    @DisplayName("handleDispute: 跑腿纠纷裁定退款后返还发布者并将剩余金额结算给接单人")
    void handleDispute_errandResolved_refundPublisherAndPayAcceptor() {
        DisputeRecord record = new DisputeRecord();
        record.setRecordId(20L);
        record.setInitiatorId(100L);
        record.setRelatedId(200L);
        record.setContentId(300L);
        record.setTargetType(DisputeTargetType.ERRAND.getCode());
        record.setSchoolCode("SC001");
        record.setCampusCode("C001");
        record.setHandleStatus(DisputeStatus.PENDING.getCode());
        record.setClaimRefund(1);
        record.setClaimRefundAmount(new BigDecimal("30.00"));

        ErrandTask task = new ErrandTask();
        task.setTaskId(300L);
        task.setPublisherId(100L);
        task.setAcceptorId(200L);
        task.setReward(new BigDecimal("30.00"));
        task.setTaskStatus(ErrandStatus.PENDING_CONFIRM.getCode());

        when(disputeRecordMapper.selectByIdForUpdate(20L)).thenReturn(record);
        when(errandTaskMapper.selectByIdForUpdate(300L)).thenReturn(task);
        org.mockito.Mockito.lenient().when(userInfoMapper.creditBalance(100L, new BigDecimal("30.00"))).thenReturn(1);
        when(disputeRecordMapper.updateById(any(DisputeRecord.class))).thenReturn(1);

        disputeDomainService.handleDispute(
                1L,
                20L,
                "跑腿未按要求完成，退回全部悬赏",
                DisputeStatus.RESOLVED.getCode(),
                null,
                new BigDecimal("30.00")
        );

        verify(userInfoMapper).creditBalance(100L, new BigDecimal("30.00"));
        verify(errandTaskMapper).updateById(task);
        verify(userInfoMapper, never()).updateById(any(UserInfo.class));
        verify(noticeService).sendNotice(
                100L,
                "纠纷处理结果",
                "您发起的纠纷已处理，结果：退款¥30；处理说明：跑腿未按要求完成，退回全部悬赏",
                NoticeType.DISPUTE.getCode(),
                20L
        );
    }

    @Test
    void handleDispute_pendingShipmentRejectsPartialSettlement() {
        DisputeRecord record = orderDispute();
        OrderInfo order = disputedOrder(OrderStatus.PENDING_DELIVERY.getCode());
        when(disputeRecordMapper.selectByIdForUpdate(30L)).thenReturn(record);
        org.mockito.Mockito.lenient().when(orderInfoMapper.selectByIdForUpdate(400L)).thenReturn(order);
        org.mockito.Mockito.lenient().when(userInfoMapper.creditBalance(100L, new BigDecimal("20.00"))).thenReturn(1);
        org.mockito.Mockito.lenient().when(userInfoMapper.creditBalance(200L, new BigDecimal("80.00"))).thenReturn(1);

        assertThrows(BusinessException.class, () -> disputeDomainService.handleDispute(
                1L, 30L, "裁定部分退款", DisputeStatus.RESOLVED.getCode(), null, new BigDecimal("20.00")));
        verify(userInfoMapper, never()).creditBalance(any(), any());
        assertEquals(OrderStatus.PENDING_DELIVERY.getCode(), order.getOrderStatus());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
    }

    @Test
    void handleDispute_rejectedUndeliveredClaimKeepsEscrowUntilDelivery() {
        DisputeRecord record = orderDispute();
        OrderInfo order = disputedOrder(OrderStatus.PENDING_DELIVERY.getCode());
        when(disputeRecordMapper.selectByIdForUpdate(30L)).thenReturn(record);
        when(orderInfoMapper.selectByIdForUpdate(400L)).thenReturn(order);
        disputeDomainService.handleDispute(1L, 30L, "未支持退款诉求", DisputeStatus.REJECTED.getCode(), null, null);
        assertEquals(OrderStatus.PENDING_DELIVERY.getCode(), order.getOrderStatus());
        verify(userInfoMapper, never()).creditBalance(any(), any());
        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verify(disputeRecordMapper).updateById(record);
    }

    @Test
    void handleDispute_missingRefundRecipientDoesNotCloseOrderOrDispute() {
        DisputeRecord record = orderDispute();
        OrderInfo order = disputedOrder(OrderStatus.PENDING_RECEIVE.getCode());
        when(disputeRecordMapper.selectByIdForUpdate(30L)).thenReturn(record);
        org.mockito.Mockito.lenient().when(orderInfoMapper.selectByIdForUpdate(400L)).thenReturn(order);

        assertThrows(BusinessException.class, () -> disputeDomainService.handleDispute(
                1L, 30L, "退款", DisputeStatus.RESOLVED.getCode(), null, new BigDecimal("20.00")));

        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
        verify(disputeRecordMapper, never()).updateById(any(DisputeRecord.class));
        verify(noticeService, never()).sendNotice(any(), any(), any(), any(), any());
    }

    private DisputeRecord orderDispute() {
        DisputeRecord record = new DisputeRecord();
        record.setRecordId(30L);
        record.setContentId(400L);
        record.setInitiatorId(100L);
        record.setRelatedId(200L);
        record.setTargetType(DisputeTargetType.ORDER.getCode());
        record.setHandleStatus(DisputeStatus.PENDING.getCode());
        record.setClaimRefund(1);
        record.setClaimRefundAmount(new BigDecimal("100.00"));
        return record;
    }

    @Test
    void handleDispute_rejectsFractionalCentRefundBeforeCreditingEitherParty() {
        DisputeRecord record = orderDispute();
        when(disputeRecordMapper.selectByIdForUpdate(30L)).thenReturn(record);
        org.mockito.Mockito.lenient().when(orderInfoMapper.selectByIdForUpdate(400L))
                .thenReturn(disputedOrder(OrderStatus.PENDING_RECEIVE.getCode()));
        org.mockito.Mockito.lenient().when(userInfoMapper.creditBalance(anyLong(), any())).thenReturn(1);

        assertThrows(BusinessException.class, () -> disputeDomainService.handleDispute(
                1L, 30L, "退款", DisputeStatus.RESOLVED.getCode(), null, new BigDecimal("0.005")));

        verify(userInfoMapper, never()).creditBalance(anyLong(), any());
        verify(disputeRecordMapper, never()).updateById(any(DisputeRecord.class));
    }

    @Test
    void handleDispute_fullRefundLocksLatestGoodsBeforeRestoringStock() {
        DisputeRecord record = orderDispute();
        OrderInfo order = disputedOrder(OrderStatus.PENDING_RECEIVE.getCode());
        order.setProductId(500L);
        GoodsInfo goods = new GoodsInfo();
        goods.setProductId(500L);
        goods.setTradeStatus(1);
        goods.setCollectCount(8);
        when(disputeRecordMapper.selectByIdForUpdate(30L)).thenReturn(record);
        when(orderInfoMapper.selectByIdForUpdate(400L)).thenReturn(order);
        when(userInfoMapper.creditBalance(100L, new BigDecimal("100.00"))).thenReturn(1);
        org.mockito.Mockito.lenient().when(goodsInfoMapper.selectByIdForUpdate(500L)).thenReturn(goods);

        disputeDomainService.handleDispute(1L, 30L, "全额退款", DisputeStatus.RESOLVED.getCode(), null, new BigDecimal("100.00"));

        verify(goodsInfoMapper).updateById(goods);
        assertEquals(2, goods.getTradeStatus());
        assertEquals(8, goods.getCollectCount());
    }

    @Test
    void handleDispute_rejectsInvalidOrderEscrowBeforeClosingDispute() {
        DisputeRecord record = orderDispute();
        record.setClaimRefund(0);
        OrderInfo order = disputedOrder(OrderStatus.PENDING_RECEIVE.getCode());
        order.setTotalAmount(new BigDecimal("-100.00"));
        when(disputeRecordMapper.selectByIdForUpdate(30L)).thenReturn(record);
        when(orderInfoMapper.selectByIdForUpdate(400L)).thenReturn(order);

        assertThrows(BusinessException.class, () -> disputeDomainService.handleDispute(
                1L, 30L, "结算", DisputeStatus.RESOLVED.getCode(), null, null));

        verify(orderInfoMapper, never()).updateById(any(OrderInfo.class));
    }

    @Test
    void handleDispute_rejectsInvalidErrandEscrowBeforeClosingDispute() {
        DisputeRecord record = orderDispute();
        record.setTargetType(DisputeTargetType.ERRAND.getCode());
        record.setClaimRefund(0);
        ErrandTask task = new ErrandTask();
        task.setTaskId(400L);
        task.setTaskStatus(ErrandStatus.PENDING_CONFIRM.getCode());
        task.setReward(new BigDecimal("-10.00"));
        when(disputeRecordMapper.selectByIdForUpdate(30L)).thenReturn(record);
        when(errandTaskMapper.selectByIdForUpdate(400L)).thenReturn(task);

        assertThrows(BusinessException.class, () -> disputeDomainService.handleDispute(
                1L, 30L, "结算", DisputeStatus.RESOLVED.getCode(), null, null));

        verify(errandTaskMapper, never()).updateById(any(ErrandTask.class));
    }

    @Test
    void handleDispute_usesCurrentLockedRecordBeforeRepeatingCreditPenalty() {
        DisputeRecord stale = new DisputeRecord();
        stale.setRecordId(30L);
        stale.setRelatedId(200L);
        stale.setHandleStatus(DisputeStatus.PENDING.getCode());
        stale.setClaimSellerCreditPenalty(1);
        DisputeRecord committed = new DisputeRecord();
        committed.setRecordId(30L);
        committed.setRelatedId(200L);
        committed.setHandleStatus(DisputeStatus.RESOLVED.getCode());
        committed.setHandleResult("处理完成");
        DisputeRecordMapper guardedMapper = org.mockito.Mockito.mock(DisputeRecordMapper.class, invocation -> {
            if (invocation.getMethod().getName().equals("selectById")) {
                return stale;
            }
            if (invocation.getMethod().getName().equals("selectByIdForUpdate")) {
                return committed;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        ReflectionTestUtils.setField(disputeDomainService, "disputeRecordMapper", guardedMapper);

        disputeDomainService.handleDispute(1L, 30L, "处理完成", DisputeStatus.RESOLVED.getCode(), 5, null);

        verify(creditScoreService, never()).adjustCreditScore(anyLong(), anyInt(), anyString());
        verify(guardedMapper, never()).updateById(any(DisputeRecord.class));
        verify(noticeService, never()).sendNotice(any(), any(), any(), any(), any());
    }

    private OrderInfo disputedOrder(Integer status) {
        OrderInfo order = new OrderInfo();
        order.setOrderId(400L);
        order.setBuyerId(100L);
        order.setSellerId(200L);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setOrderStatus(status);
        return order;
    }
}
