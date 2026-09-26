package com.unimarket.module.errand.listener;

import com.unimarket.common.enums.ErrandStatus;
import com.unimarket.common.exception.BusinessException;
import com.unimarket.module.errand.dto.ErrandAutoConfirmMessage;
import com.unimarket.module.errand.entity.ErrandTask;
import com.unimarket.module.errand.mapper.ErrandTaskMapper;
import com.unimarket.module.notice.service.NoticeService;
import com.unimarket.module.user.mapper.UserInfoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErrandAutoConfirmListenerTest {
    @Mock private ErrandTaskMapper errandTaskMapper;
    @Mock private UserInfoMapper userInfoMapper;
    @Mock private NoticeService noticeService;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock lock;
    @InjectMocks private ErrandAutoConfirmListener listener;

    @Test
    void missingRecipientDoesNotCompleteOrAnnouncePayment() throws Exception {
        ErrandTask task = new ErrandTask();
        task.setTaskId(9L);
        task.setAcceptorId(2L);
        task.setReward(new BigDecimal("12.50"));
        task.setTaskStatus(ErrandStatus.PENDING_CONFIRM.getCode());
        when(redissonClient.getLock("errand:lock:lifecycle:9")).thenReturn(lock);
        when(lock.tryLock(0, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(errandTaskMapper.selectByIdForUpdate(9L)).thenReturn(task);
        ErrandAutoConfirmMessage message = new ErrandAutoConfirmMessage();
        message.setTaskId(9L);

        assertThrows(BusinessException.class, () -> listener.onMessage(message));

        verify(errandTaskMapper, never()).updateById(any(ErrandTask.class));
        verifyNoInteractions(noticeService);
    }
}
