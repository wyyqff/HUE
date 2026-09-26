package com.unimarket.admin.service;

import com.unimarket.admin.service.impl.domain.AdminErrandDomainService;
import com.unimarket.admin.service.impl.support.AdminActionLockSupport;
import com.unimarket.admin.service.impl.support.AdminScopeSupport;
import com.unimarket.admin.service.impl.support.AdminSchoolInfoSupport;
import com.unimarket.common.enums.ReviewStatus;
import com.unimarket.common.enums.ErrandStatus;
import com.unimarket.common.exception.BusinessException;
import com.unimarket.module.errand.entity.ErrandTask;
import com.unimarket.module.errand.mapper.ErrandTaskMapper;
import com.unimarket.module.iam.service.IamAccessService;
import com.unimarket.module.notice.service.NoticeService;
import com.unimarket.module.user.entity.UserInfo;
import com.unimarket.module.user.mapper.UserInfoMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminErrandDomainServiceConcurrencyTest {

    @Mock
    private ErrandTaskMapper errandTaskMapper;
    @Mock
    private UserInfoMapper userInfoMapper;
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
    private AdminErrandDomainService errandDomainService;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(actionLockSupport).withLock(anyString(), any(Runnable.class));
    }

    @Test
    @DisplayName("auditErrand: 终态同结果请求幂等忽略")
    void auditErrand_terminalSameStatus_idempotentIgnore() {
        ErrandTask task = new ErrandTask();
        task.setTaskId(100L);
        task.setPublisherId(200L);
        task.setSchoolCode("SC001");
        task.setCampusCode("CP001");
        task.setReviewStatus(ReviewStatus.MANUAL_PASSED.getCode());
        when(errandTaskMapper.selectByIdForUpdate(100L)).thenReturn(task);

        errandDomainService.auditErrand(1L, 100L, 1, null);

        verify(errandTaskMapper, never()).updateById(any(ErrandTask.class));
        verify(noticeService, never()).sendNotice(any(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("auditErrand: 终态冲突请求抛出异常")
    void auditErrand_terminalConflict_throw() {
        ErrandTask task = new ErrandTask();
        task.setTaskId(100L);
        task.setPublisherId(200L);
        task.setSchoolCode("SC001");
        task.setCampusCode("CP001");
        task.setReviewStatus(ReviewStatus.REJECTED.getCode());
        when(errandTaskMapper.selectByIdForUpdate(100L)).thenReturn(task);

        assertThrows(BusinessException.class, () -> errandDomainService.auditErrand(1L, 100L, 1, "改判"));
        verify(errandTaskMapper, never()).updateById(any(ErrandTask.class));
    }

    @Test
    @DisplayName("auditErrand: 驳回时退还悬赏金额")
    void auditErrand_rejectRefundsReward() {
        ErrandTask task = new ErrandTask();
        task.setTaskId(100L);
        task.setPublisherId(200L);
        task.setSchoolCode("SC001");
        task.setCampusCode("CP001");
        task.setTitle("帮取外卖");
        task.setReward(new BigDecimal("18.00"));
        task.setReviewStatus(ReviewStatus.WAIT_MANUAL.getCode());

        when(errandTaskMapper.selectByIdForUpdate(100L)).thenReturn(task);
        org.mockito.Mockito.lenient().when(userInfoMapper.creditBalance(200L, new BigDecimal("18.00"))).thenReturn(1);

        assertDoesNotThrow(() -> errandDomainService.auditErrand(1L, 100L, 2, "违规内容"));

        verify(userInfoMapper).creditBalance(200L, new BigDecimal("18.00"));
        verify(userInfoMapper, never()).updateById(any(UserInfo.class));
        verify(errandTaskMapper).updateById(task);
        verify(noticeService).sendNotice(200L, "跑腿任务审核未通过", "您的跑腿任务【帮取外卖】未通过人工复核，悬赏金额已退回余额。原因：违规内容", 1, 100L);
        verify(rocketMQTemplate).convertAndSend("errand-sync-topic", com.unimarket.common.mq.ErrandSyncMessage.deleteMessage(100L));
    }

    @Test
    void auditErrand_cancelledTaskCannotRefundEscrowAgain() {
        ErrandTask task = new ErrandTask();
        task.setTaskId(101L);
        task.setPublisherId(201L);
        task.setReward(new BigDecimal("18.00"));
        task.setReviewStatus(ReviewStatus.WAIT_MANUAL.getCode());
        task.setTaskStatus(ErrandStatus.CANCELLED.getCode());
        when(errandTaskMapper.selectByIdForUpdate(101L)).thenReturn(task);
        org.mockito.Mockito.lenient().when(userInfoMapper.creditBalance(201L, new BigDecimal("18.00"))).thenReturn(1);

        assertThrows(BusinessException.class, () -> errandDomainService.auditErrand(1L, 101L, 2, "违规内容"));

        verify(userInfoMapper, never()).creditBalance(any(), any());
        verify(errandTaskMapper, never()).updateById(any(ErrandTask.class));
    }
}


