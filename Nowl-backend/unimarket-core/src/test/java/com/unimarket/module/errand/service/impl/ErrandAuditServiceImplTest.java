package com.unimarket.module.errand.service.impl;

import com.unimarket.ai.dto.AiAuditResult;
import com.unimarket.common.enums.ErrandStatus;
import com.unimarket.common.enums.ReviewStatus;
import com.unimarket.common.exception.BusinessException;
import com.unimarket.common.utils.RedisCache;
import com.unimarket.common.mq.ErrandSyncMessage;
import com.unimarket.module.errand.entity.ErrandTask;
import com.unimarket.module.errand.mapper.ErrandTaskMapper;
import com.unimarket.module.notice.service.NoticeService;
import com.unimarket.module.user.entity.UserInfo;
import com.unimarket.module.user.mapper.UserInfoMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErrandAuditServiceImplTest {

    @Mock
    private ErrandTaskMapper errandTaskMapper;
    @Mock
    private com.unimarket.ai.service.AiAuditService aiAuditService;
    @Mock
    private NoticeService noticeService;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private UserInfoMapper userInfoMapper;
    @Mock
    private RedisCache redisCache;

    @InjectMocks
    private ErrandAuditServiceImpl errandAuditService;

    @Test
    void performAudit_cancelledTaskDoesNotRefundEscrowAgain() {
        ErrandTask task = new ErrandTask();
        task.setTaskId(103L);
        task.setPublisherId(203L);
        task.setTitle("已取消任务");
        task.setReward(new BigDecimal("12.50"));
        task.setTaskStatus(ErrandStatus.CANCELLED.getCode());
        task.setReviewStatus(ReviewStatus.WAIT_REVIEW.getCode());
        when(errandTaskMapper.selectByIdForUpdate(103L)).thenReturn(task);
        org.mockito.Mockito.lenient().when(aiAuditService.auditText(anyString())).thenReturn(new AiAuditResult(false, "违规内容", "high"));
        org.mockito.Mockito.lenient().when(errandTaskMapper.update(eq(null), any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(userInfoMapper.creditBalance(203L, new BigDecimal("12.50"))).thenReturn(1);

        errandAuditService.performAudit(103L, 1);

        verify(userInfoMapper, never()).creditBalance(any(), any());
        verify(errandTaskMapper, never()).update(eq(null), any());
    }

    @Test
    @DisplayName("performAudit: AI驳回时退还悬赏金额并下线任务")
    void performAudit_rejectedRefundsRewardAndDeletesIndex() {
        ErrandTask task = new ErrandTask();
        task.setTaskId(100L);
        task.setPublisherId(200L);
        task.setTitle("帮我取快递");
        task.setReward(new BigDecimal("12.50"));
        task.setTaskStatus(ErrandStatus.PENDING.getCode());
        task.setReviewStatus(ReviewStatus.WAIT_REVIEW.getCode());

        when(errandTaskMapper.selectByIdForUpdate(100L)).thenReturn(task);
        when(aiAuditService.auditText(anyString())).thenReturn(new AiAuditResult(false, "违规内容", "high"));
        when(errandTaskMapper.update(eq(null), any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(userInfoMapper.creditBalance(200L, new BigDecimal("12.50"))).thenReturn(1);

        assertDoesNotThrow(() -> errandAuditService.performAudit(100L, 1));

        verify(userInfoMapper).creditBalance(200L, new BigDecimal("12.50"));
        verify(userInfoMapper, never()).updateById(any(UserInfo.class));
        assertEquals(ReviewStatus.REJECTED.getCode(), task.getReviewStatus());
        assertEquals("违规内容", task.getAuditReason());
        verify(errandTaskMapper).update(eq(null), any());
        verify(noticeService).sendNotice(eq(200L), eq("跑腿任务审核未通过"), eq("您的跑腿任务【帮我取快递】未通过审核，悬赏金额已退回余额。原因：违规内容"), eq(1), eq(100L));

        ArgumentCaptor<ErrandSyncMessage> captor = ArgumentCaptor.forClass(ErrandSyncMessage.class);
        verify(rocketMQTemplate).convertAndSend(eq("errand-sync-topic"), captor.capture());
        assertEquals(ErrandSyncMessage.SyncType.DELETE, captor.getValue().getType());
        assertEquals(100L, captor.getValue().getTaskId());
    }

    @Test
    void performAudit_failedRefundStopsSuccessNotice() {
        ErrandTask task = new ErrandTask();
        task.setTaskId(102L);
        task.setPublisherId(202L);
        task.setTitle("帮我取快递");
        task.setReward(new BigDecimal("12.50"));
        task.setTaskStatus(ErrandStatus.PENDING.getCode());
        task.setReviewStatus(ReviewStatus.WAIT_REVIEW.getCode());
        when(errandTaskMapper.selectByIdForUpdate(102L)).thenReturn(task);
        when(aiAuditService.auditText(anyString())).thenReturn(new AiAuditResult(false, "违规内容", "high"));
        when(errandTaskMapper.update(eq(null), any())).thenReturn(1);

        assertThrows(BusinessException.class, () -> errandAuditService.performAudit(102L, 1));

        verify(noticeService, never()).sendNotice(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("performAudit: 审核结果已落库时跳过重复退款")
    void performAudit_duplicateMessageDoesNotRefundAgain() {
        ErrandTask task = new ErrandTask();
        task.setTaskId(101L);
        task.setPublisherId(201L);
        task.setTitle("帮我拿文件");
        task.setReward(new BigDecimal("8.00"));
        task.setTaskStatus(ErrandStatus.PENDING.getCode());
        task.setReviewStatus(ReviewStatus.WAIT_REVIEW.getCode());

        when(errandTaskMapper.selectByIdForUpdate(101L)).thenReturn(task);
        when(aiAuditService.auditText(anyString())).thenReturn(new AiAuditResult(false, "重复消息", "high"));
        when(errandTaskMapper.update(eq(null), any())).thenReturn(0);

        errandAuditService.performAudit(101L, 1);

        verify(userInfoMapper, never()).selectById(any(Long.class));
        verify(userInfoMapper, never()).updateById(any(UserInfo.class));
        verify(noticeService, never()).sendNotice(any(Long.class), anyString(), anyString(), any(Integer.class), any(Long.class));
        verify(rocketMQTemplate, never()).convertAndSend(anyString(), any(ErrandSyncMessage.class));
    }
}


