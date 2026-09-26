package com.unimarket.module.errand.service.impl;

import com.unimarket.common.enums.ErrandStatus;
import com.unimarket.common.enums.ReviewStatus;
import com.unimarket.common.exception.BusinessException;
import com.unimarket.common.utils.RedisCache;
import com.unimarket.module.errand.entity.ErrandTask;
import com.unimarket.module.errand.dto.ErrandPublishDTO;
import com.unimarket.module.errand.mapper.ErrandTaskMapper;
import com.unimarket.module.errand.service.ErrandDelayMessageService;
import com.unimarket.module.notice.service.NoticeService;
import com.unimarket.module.risk.service.RiskControlService;
import com.unimarket.module.school.mapper.SchoolInfoMapper;
import com.unimarket.module.school.entity.SchoolInfo;
import com.unimarket.module.user.entity.UserInfo;
import com.unimarket.module.user.mapper.UserInfoMapper;
import com.unimarket.security.UserContext;
import com.unimarket.security.UserContextHolder;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErrandServiceImplTest {

    @Mock
    private ErrandTaskMapper errandTaskMapper;
    @Mock
    private UserInfoMapper userInfoMapper;
    @Mock
    private SchoolInfoMapper schoolInfoMapper;
    @Mock
    private NoticeService noticeService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private ErrandDelayMessageService errandDelayMessageService;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private RiskControlService riskControlService;
    @Mock
    private RLock lock;
    @Mock
    private RedisCache redisCache;

    @InjectMocks
    private ErrandServiceImpl errandService;

    @Test
    void confirmErrand_readsCommittedTaskBeforeRepeatingSettlement() throws Exception {
        ErrandTask stale = new ErrandTask();
        stale.setTaskId(92L);
        stale.setAcceptorId(2L);
        stale.setReward(new BigDecimal("12.50"));
        stale.setTaskStatus(ErrandStatus.PENDING_CONFIRM.getCode());
        ErrandTask committed = new ErrandTask();
        committed.setTaskId(92L);
        committed.setTaskStatus(ErrandStatus.COMPLETED.getCode());
        ErrandTaskMapper guardedMapper = org.mockito.Mockito.mock(ErrandTaskMapper.class, invocation -> {
            if (invocation.getMethod().getName().equals("selectById")) return stale;
            if (invocation.getMethod().getName().equals("selectByIdForUpdate")) return committed;
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        ReflectionTestUtils.setField(errandService, "errandTaskMapper", guardedMapper);
        when(redissonClient.getLock("errand:lock:lifecycle:92")).thenReturn(lock);
        when(lock.tryLock(3, 10, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        org.mockito.Mockito.lenient().when(userInfoMapper.creditBalance(2L, new BigDecimal("12.50"))).thenReturn(1);

        assertThrows(BusinessException.class, () -> errandService.confirmErrand(92L));

        verify(userInfoMapper, never()).creditBalance(any(), any());
        verify(guardedMapper, never()).updateById(any(ErrandTask.class));
    }

    @Test
    void publishErrand_failedAtomicDebitDoesNotCreateTask() {
        UserInfo publisher = new UserInfo();
        publisher.setUserId(1L);
        publisher.setMoney(new BigDecimal("100.00"));
        ErrandPublishDTO dto = new ErrandPublishDTO();
        dto.setReward(new BigDecimal("80.00"));
        when(userInfoMapper.selectById(1L)).thenReturn(publisher);
        when(schoolInfoMapper.selectOne(any())).thenReturn(new SchoolInfo());

        assertThrows(BusinessException.class, () -> errandService.publishErrand(1L, dto));

        verify(errandTaskMapper, never()).insert(any(ErrandTask.class));
        verify(userInfoMapper, never()).updateById(any(UserInfo.class));
    }

    @Test
    void increaseReward_failedAtomicDebitDoesNotUpdateTask() {
        ErrandTask task = new ErrandTask();
        task.setTaskId(90L);
        task.setPublisherId(1L);
        task.setReward(new BigDecimal("10.00"));
        task.setTaskStatus(ErrandStatus.PENDING.getCode());
        UserInfo publisher = new UserInfo();
        publisher.setMoney(new BigDecimal("100.00"));
        ErrandPublishDTO dto = new ErrandPublishDTO();
        dto.setReward(new BigDecimal("80.00"));
        when(userInfoMapper.selectById(1L)).thenReturn(publisher);
        when(errandTaskMapper.selectByIdForUpdate(90L)).thenReturn(task);

        assertThrows(BusinessException.class, () -> errandService.updateErrand(1L, 90L, dto));

        verify(errandTaskMapper, never()).updateById(any(ErrandTask.class));
    }

    @Test
    void cancelErrand_missingPublisherDoesNotCancelOrAnnounceRefund() throws Exception {
        ErrandTask task = new ErrandTask();
        task.setTaskId(91L);
        task.setPublisherId(1L);
        task.setReward(new BigDecimal("12.50"));
        task.setTaskStatus(ErrandStatus.PENDING.getCode());
        when(redissonClient.getLock("errand:lock:lifecycle:91")).thenReturn(lock);
        when(lock.tryLock(3, 10, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(errandTaskMapper.selectByIdForUpdate(91L)).thenReturn(task);
        UserContext context = new UserContext();
        context.setUserId(1L);
        UserContextHolder.setContext(context);
        try {
            assertThrows(BusinessException.class, () -> errandService.cancelErrand(91L, "取消"));
        } finally {
            UserContextHolder.clear();
        }
        verify(errandTaskMapper, never()).updateById(any(ErrandTask.class));
        verify(noticeService, never()).sendNotice(any(), any(), any(), any(), any());
    }

    @Test
    void confirmErrand_missingRecipient_doesNotCompleteOrAnnouncePayment() throws Exception {
        ErrandTask task = new ErrandTask();
        task.setTaskId(89L);
        task.setPublisherId(1L);
        task.setAcceptorId(2L);
        task.setReward(new BigDecimal("12.50"));
        task.setTaskStatus(ErrandStatus.PENDING_CONFIRM.getCode());
        when(redissonClient.getLock("errand:lock:lifecycle:89")).thenReturn(lock);
        when(lock.tryLock(3, 10, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(errandTaskMapper.selectByIdForUpdate(89L)).thenReturn(task);

        assertThrows(BusinessException.class, () -> errandService.confirmErrand(89L));

        verify(errandTaskMapper, never()).updateById(any(ErrandTask.class));
        verify(noticeService, never()).sendNotice(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("cancelErrand: 审核驳回任务不可重复取消退款")
    void cancelErrand_rejectedTask_throwWithoutRefund() throws Exception {
        ErrandTask task = new ErrandTask();
        task.setTaskId(88L);
        task.setPublisherId(1L);
        task.setTaskStatus(ErrandStatus.PENDING.getCode());
        task.setReviewStatus(ReviewStatus.REJECTED.getCode());

        when(redissonClient.getLock("errand:lock:lifecycle:88")).thenReturn(lock);
        when(lock.tryLock(3, 10, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
        when(errandTaskMapper.selectByIdForUpdate(88L)).thenReturn(task);

        assertThrows(BusinessException.class, () -> errandService.cancelErrand(88L, "不想发了"));

        verify(userInfoMapper, never()).selectById(any());
        verify(errandTaskMapper, never()).updateById(any(ErrandTask.class));
        verify(noticeService, never()).sendNotice(any(), any(), any(), any(), any());
        verify(lock, never()).unlock();
    }
}

