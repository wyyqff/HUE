package com.unimarket.module.dispute.service;

import com.unimarket.common.exception.BusinessException;
import com.unimarket.module.dispute.dto.OrderDisputeApplyDTO;
import com.unimarket.module.dispute.entity.DisputeRecord;
import com.unimarket.module.dispute.mapper.DisputeRecordMapper;
import com.unimarket.module.dispute.service.impl.OrderDisputeServiceImpl;
import com.unimarket.module.order.entity.OrderInfo;
import com.unimarket.module.order.mapper.OrderInfoMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrderDisputeRefundRejectionTest {
    @Test
    void buyerCanAppealRejectedRefundBeforeDelivery() {
        var orders = mock(OrderInfoMapper.class);
        var disputes = mock(DisputeRecordMapper.class);
        var order = pendingOrder();
        when(orders.selectById(1L)).thenReturn(order);
        when(orders.selectByIdForUpdate(1L)).thenReturn(order);
        var dto = request();
        var service = new OrderDisputeServiceImpl(orders, disputes);
        assertDoesNotThrow(() -> service.applyDispute(10L, dto));
        verify(disputes).insert(any(DisputeRecord.class));
    }

    @Test
    void sellerCannotUseBuyerRefundAppealBeforeDelivery() {
        var orders = mock(OrderInfoMapper.class);
        var disputes = mock(DisputeRecordMapper.class);
        var order = pendingOrder();
        when(orders.selectById(1L)).thenReturn(order);
        when(orders.selectByIdForUpdate(1L)).thenReturn(order);
        var service = new OrderDisputeServiceImpl(orders, disputes);
        assertThrows(BusinessException.class, () -> service.applyDispute(20L, request()));
        verify(disputes, never()).insert(any(DisputeRecord.class));
    }

    private OrderInfo pendingOrder() {
        var order = new OrderInfo();
        order.setOrderId(1L);
        order.setBuyerId(10L);
        order.setSellerId(20L);
        order.setOrderStatus(1);
        order.setRefundStatus(3);
        return order;
    }

    private OrderDisputeApplyDTO request() {
        var dto = new OrderDisputeApplyDTO();
        dto.setOrderId(1L);
        dto.setReason("未交付且卖家拒绝退款");
        return dto;
    }
}
