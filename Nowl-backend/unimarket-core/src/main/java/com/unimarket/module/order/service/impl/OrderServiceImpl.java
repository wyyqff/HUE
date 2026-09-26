package com.unimarket.module.order.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unimarket.common.enums.DisputeStatus;
import com.unimarket.common.enums.DisputeTargetType;
import com.unimarket.common.config.RocketMQConfig;
import com.unimarket.common.enums.NoticeType;
import com.unimarket.common.enums.OrderStatus;
import com.unimarket.common.enums.RefundStatus;
import com.unimarket.common.enums.ReviewStatus;
import com.unimarket.common.enums.TradeStatus;
import com.unimarket.common.exception.BusinessException;
import com.unimarket.common.mq.GoodsSyncMessage;
import com.unimarket.common.result.PageResult;
import com.unimarket.common.result.ResultCode;
import com.unimarket.common.utils.MoneyValidator;
import com.unimarket.module.goods.entity.GoodsInfo;
import com.unimarket.module.goods.mapper.GoodsInfoMapper;
import com.unimarket.module.dispute.entity.DisputeRecord;
import com.unimarket.module.dispute.mapper.DisputeRecordMapper;
import com.unimarket.module.order.dto.OrderCreateDTO;
import com.unimarket.module.order.dto.OrderQueryDTO;
import com.unimarket.module.order.dto.RefundApplyDTO;
import com.unimarket.module.order.dto.RefundProcessDTO;
import com.unimarket.module.order.entity.OrderInfo;
import com.unimarket.module.order.mapper.OrderInfoMapper;
import com.unimarket.module.order.service.OrderDelayMessageService;
import com.unimarket.module.order.service.OrderService;
import com.unimarket.module.order.vo.OrderVO;
import com.unimarket.module.notice.service.NoticeService;
import com.unimarket.module.user.entity.UserInfo;
import com.unimarket.module.user.mapper.UserInfoMapper;
import com.unimarket.module.user.service.CreditScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderInfoMapper orderInfoMapper;
    private final GoodsInfoMapper goodsInfoMapper;
    private final UserInfoMapper userInfoMapper;
    private final NoticeService noticeService;
    private final DisputeRecordMapper disputeRecordMapper;
    private final RedissonClient redissonClient;
    private final OrderDelayMessageService orderDelayMessageService;
    private final CreditScoreService creditScoreService;
    private final RocketMQTemplate rocketMQTemplate;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(Long userId, OrderCreateDTO dto) {
        // 获取分布式锁，锁粒度为商品ID，防止同一商品并发下单
        String lockKey = "order:lock:goods:" + dto.getProductId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，等待3秒，持有10秒自动释放
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            // 1. 查询买家信息（权限校验已在Controller层通过@PreAuthorize完成）
            UserInfo buyer = userInfoMapper.selectById(userId);
            if (buyer == null) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND);
            }

            // 2. 查询商品信息
            GoodsInfo goodsInfo = goodsInfoMapper.selectByIdForUpdate(dto.getProductId());
            if (goodsInfo == null) {
                throw new BusinessException("商品不存在");
            }

            // 检查商品状态
            if (!TradeStatus.ON_SALE.getCode().equals(goodsInfo.getTradeStatus())) {
                throw new BusinessException("商品已下架或已售出");
            }
            if (!ReviewStatus.AI_PASSED.getCode().equals(goodsInfo.getReviewStatus())
                    && !ReviewStatus.MANUAL_PASSED.getCode().equals(goodsInfo.getReviewStatus())) {
                throw new BusinessException("商品未通过审核，暂不可购买");
            }

            // 不能购买自己的商品
            if (goodsInfo.getSellerId().equals(userId)) {
                throw new BusinessException("不能购买自己的商品");
            }

            // 校园隔离：仅允许同学校下单（校区可不同）
            String buyerSchoolCode = buyer.getSchoolCode();
            String goodsSchoolCode = goodsInfo.getSchoolCode();
            if (StrUtil.isBlank(buyerSchoolCode) || StrUtil.isBlank(goodsSchoolCode)) {
                throw new BusinessException("用户或商品学校信息缺失，暂不可下单");
            }
            if (!buyerSchoolCode.trim().equalsIgnoreCase(goodsSchoolCode.trim())) {
                throw new BusinessException("仅支持同学校范围内下单");
            }

            MoneyValidator.requireValid(goodsInfo.getPrice(), false, "商品价格");
            BigDecimal advertisedFee = goodsInfo.getDeliveryFee() == null ? BigDecimal.ZERO : goodsInfo.getDeliveryFee();
            MoneyValidator.requireValid(advertisedFee, true, "运费");
            int tradeType = resolveTradeType(goodsInfo.getTradeType(), dto.getTradeType());
            BigDecimal deliveryFee = tradeType == 0 ? BigDecimal.ZERO : advertisedFee;
            MoneyValidator.requireValid(goodsInfo.getPrice().add(deliveryFee), false, "订单总额");

            // 创建订单
            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setOrderNo(generateOrderNo());
            orderInfo.setBuyerId(userId);
            orderInfo.setSellerId(goodsInfo.getSellerId());
            orderInfo.setProductId(goodsInfo.getProductId());
            orderInfo.setSchoolCode(goodsInfo.getSchoolCode());
            orderInfo.setCampusCode(goodsInfo.getCampusCode());
            orderInfo.setOrderAmount(goodsInfo.getPrice());
            orderInfo.setTradeType(tradeType);
            orderInfo.setDeliveryFee(deliveryFee);
            orderInfo.setTotalAmount(orderInfo.getOrderAmount().add(orderInfo.getDeliveryFee()));
            orderInfo.setOrderStatus(OrderStatus.PENDING_PAYMENT.getCode()); // 待支付
            orderInfo.setRemark(dto.getRemark());
            orderInfo.setRefundStatus(RefundStatus.NONE.getCode());
            orderInfo.setRefundFastTrack(0);

            int result = orderInfoMapper.insert(orderInfo);
            if (result <= 0) {
                throw new BusinessException("创建订单失败");
            }

            log.info("用户{}创建订单成功，订单号：{}", userId, orderInfo.getOrderNo());

            // 发送通知给卖家
            noticeService.sendNotice(
                    goodsInfo.getSellerId(),
                    "新订单提醒",
                    "您的商品 [" + goodsInfo.getTitle() + "] 有新订单了，请及时处理。",
                    NoticeType.TRADE.getCode()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("下单过程中断");
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public OrderVO getDetail(Long orderId) {
        // 查询订单信息
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo == null) {
            throw new BusinessException("订单不存在");
        }

        return convertToVO(orderInfo);
    }

    @Override
    public PageResult<OrderVO> getMyOrders(Long userId, OrderQueryDTO dto) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }

        // 构建查询条件
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();

        // 根据订单类型查询，避免 buy/sell 串单
        String orderType = StrUtil.blankToDefault(dto.getOrderType(), "buy").trim().toLowerCase();
        if ("buy".equals(orderType)) {
            wrapper.eq(OrderInfo::getBuyerId, userId);
        } else if ("sell".equals(orderType)) {
            wrapper.eq(OrderInfo::getSellerId, userId);
        } else {
            throw new BusinessException("订单类型不合法，仅支持 buy 或 sell");
        }

        wrapper.eq(dto.getOrderStatus() != null, OrderInfo::getOrderStatus, dto.getOrderStatus())
                .orderByDesc(OrderInfo::getCreateTime);

        // 分页查询
        Page<OrderInfo> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        Page<OrderInfo> result = orderInfoMapper.selectPage(page, wrapper);

        // 转换为VO
        List<OrderVO> voList = convertToVOList(result.getRecords());

        return new PageResult<>(result.getTotal(), voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long orderId) {
        // 获取分布式锁，锁粒度为订单ID，串行化订单状态流转
        String lockKey = "order:lock:lifecycle:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            // 查询订单
            OrderInfo orderInfo = orderInfoMapper.selectByIdForUpdate(orderId);
            if (orderInfo == null) {
                throw new BusinessException("订单不存在");
            }
            // 检查订单状态
            if (!OrderStatus.PENDING_PAYMENT.getCode().equals(orderInfo.getOrderStatus())) {
                throw new BusinessException("订单状态不正确");
            }
            validateOrderAmounts(orderInfo);
            // 再加商品维度锁，避免同一商品多个待支付订单并发支付造成超卖
            String goodsLockKey = "order:lock:goods:" + orderInfo.getProductId();
            RLock goodsLock = redissonClient.getLock(goodsLockKey);
            boolean goodsLockAcquired = false;
            try {
                goodsLockAcquired = goodsLock.tryLock(3, 10, TimeUnit.SECONDS);
                if (!goodsLockAcquired) {
                    throw new BusinessException("系统繁忙，请稍后重试");
                }
                GoodsInfo goodsInfo = goodsInfoMapper.selectByIdForUpdate(orderInfo.getProductId());
                if (goodsInfo == null) {
                    throw new BusinessException("商品不存在");
                }
                if (!TradeStatus.ON_SALE.getCode().equals(goodsInfo.getTradeStatus())) {
                    throw new BusinessException("商品已下架或售出，无法支付，请取消订单");
                }
                boolean reviewPassed = ReviewStatus.AI_PASSED.getCode().equals(goodsInfo.getReviewStatus())
                        || ReviewStatus.MANUAL_PASSED.getCode().equals(goodsInfo.getReviewStatus());
                if (!reviewPassed) {
                    throw new BusinessException("商品审核状态已变化，无法支付，请取消订单");
                }
                // 原子扣款同时检查余额；不同订单使用同一账户也不会覆盖余额。
                if (userInfoMapper.debitBalance(orderInfo.getBuyerId(), orderInfo.getTotalAmount()) != 1) {
                    throw new BusinessException("余额不足或账户不存在");
                }
                // 注意：此时不增加卖家余额，资金暂时托管在平台
                // 等买家确认收货或超时自动收货后，再转入卖家账户
                // 更新订单状态
                orderInfo.setOrderStatus(OrderStatus.PENDING_DELIVERY.getCode()); // 待发货
                orderInfo.setPayTime(LocalDateTime.now());
                orderInfoMapper.updateById(orderInfo);
                // 更新商品状态
                if (goodsInfoMapper.markSoldIfAvailable(orderInfo.getProductId()) != 1) {
                    throw new BusinessException("商品状态已变化，请重试");
                }
                notifyGoodsStatusChanged(orderInfo.getProductId());
            } finally {
                if (goodsLockAcquired && goodsLock.isHeldByCurrentThread()) {
                    goodsLock.unlock();
                }
            }
            log.info("买家{}支付订单成功，订单号：{}，金额：{}", orderInfo.getBuyerId(), orderInfo.getOrderNo(), orderInfo.getTotalAmount());

            // 发送通知给卖家
            noticeService.sendNotice(
                    orderInfo.getSellerId(),
                    "订单已支付",
                    "订单 [" + orderInfo.getOrderNo() + "] 买家已支付，请尽快发货。",
                    NoticeType.TRADE.getCode()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("支付过程中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliver(Long orderId) {
        // 获取分布式锁，锁粒度为订单ID，串行化订单状态流转
        String lockKey = "order:lock:lifecycle:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            // 查询订单
            OrderInfo orderInfo = orderInfoMapper.selectByIdForUpdate(orderId);
            if (orderInfo == null) {
                throw new BusinessException("订单不存在");
            }

            // 检查订单状态
            if (!OrderStatus.PENDING_DELIVERY.getCode().equals(orderInfo.getOrderStatus())) {
                throw new BusinessException("订单状态不正确，无法发货");
            }
            if (RefundStatus.PENDING.getCode().equals(orderInfo.getRefundStatus())) {
                throw new BusinessException("订单退款处理中，暂不可交付");
            }
            if (hasActiveOrderDispute(orderId)) {
                throw new BusinessException("订单存在进行中纠纷，暂不可交付");
            }

            // 更新订单状态
            orderInfo.setOrderStatus(OrderStatus.PENDING_RECEIVE.getCode()); // 待收货
            orderInfo.setDeliveryTime(LocalDateTime.now());
            orderInfoMapper.updateById(orderInfo);

            // 发送自动确认收货延时消息（7天后自动确认）
            long deliveryTimestamp = orderInfo.getDeliveryTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            orderDelayMessageService.sendAutoConfirmMessage(orderId, deliveryTimestamp);

            log.info("卖家{}发货成功，订单号：{}，已加入自动确认队列", orderInfo.getSellerId(), orderInfo.getOrderNo());

            // 发送通知给买家
            noticeService.sendNotice(
                    orderInfo.getBuyerId(),
                    "发货提醒",
                    "您的订单 [" + orderInfo.getOrderNo() + "] 卖家已发货，请注意查收。7天后将自动确认收货。",
                    NoticeType.TRADE.getCode()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("发货过程中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long orderId) {
        // 获取分布式锁，锁粒度为订单ID，串行化订单状态流转
        String lockKey = "order:lock:lifecycle:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            // 查询订单
            OrderInfo orderInfo = orderInfoMapper.selectByIdForUpdate(orderId);
            if (orderInfo == null) {
                throw new BusinessException("订单不存在");
            }

            // 检查订单状态
            if (!OrderStatus.PENDING_RECEIVE.getCode().equals(orderInfo.getOrderStatus())) {
                throw new BusinessException("订单状态不正确");
            }
            if (RefundStatus.PENDING.getCode().equals(orderInfo.getRefundStatus())) {
                throw new BusinessException("订单退款处理中，暂不可确认收货");
            }

            // 存在进行中纠纷时禁止确认收货，避免资金结算与仲裁冲突
            if (hasActiveOrderDispute(orderId)) {
                throw new BusinessException("订单存在进行中纠纷，暂不可确认收货");
            }

            // 资金结算：转入卖家账户
            validateOrderAmounts(orderInfo);
            if (userInfoMapper.creditBalance(orderInfo.getSellerId(), orderInfo.getTotalAmount()) != 1) {
                throw new BusinessException("卖家账户不存在，无法结算");
            }

            // 更新订单状态
            orderInfo.setOrderStatus(OrderStatus.COMPLETED.getCode()); // 已完成
            orderInfo.setReceiveTime(LocalDateTime.now());
            orderInfoMapper.updateById(orderInfo);

            log.info("买家{}确认收货成功，订单号：{}，卖家收款：{}", orderInfo.getBuyerId(), orderInfo.getOrderNo(), orderInfo.getTotalAmount());

            // 发送通知给卖家
            noticeService.sendNotice(
                    orderInfo.getSellerId(),
                    "交易完成",
                    "订单 [" + orderInfo.getOrderNo() + "] 买家已确认收货，资金已入账。",
                    NoticeType.TRADE.getCode()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("确认收货过程中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId) {
        String lockKey = "order:lock:lifecycle:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            OrderInfo orderInfo = orderInfoMapper.selectByIdForUpdate(orderId);
            if (orderInfo == null) {
                throw new BusinessException("订单不存在");
            }

            // 仅待支付订单允许取消，已支付订单需走退款流程
            if (!OrderStatus.PENDING_PAYMENT.getCode().equals(orderInfo.getOrderStatus())) {
                throw new BusinessException("仅待支付订单支持取消，请使用退款功能");
            }

            orderInfo.setOrderStatus(OrderStatus.CANCELLED.getCode());
            orderInfo.setCancelTime(LocalDateTime.now());
            orderInfoMapper.updateById(orderInfo);

            log.info("订单{}取消成功，订单号：{}", orderId, orderInfo.getOrderNo());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("取消订单过程中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyRefund(Long orderId, Long userId, RefundApplyDTO dto) {
        String lockKey = "order:lock:lifecycle:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
            OrderInfo orderInfo = orderInfoMapper.selectByIdForUpdate(orderId);
            if (orderInfo == null) {
                throw new BusinessException("订单不存在");
            }
            if (!orderInfo.getBuyerId().equals(userId)) {
                throw new BusinessException("仅买家可申请退款");
            }
            if (!OrderStatus.PENDING_DELIVERY.getCode().equals(orderInfo.getOrderStatus())
                    && !OrderStatus.PENDING_RECEIVE.getCode().equals(orderInfo.getOrderStatus())) {
                throw new BusinessException("当前订单状态不支持退款");
            }
            if (RefundStatus.PENDING.getCode().equals(orderInfo.getRefundStatus())) {
                throw new BusinessException("退款申请已在处理中");
            }
            // 存在进行中纠纷时禁止申请退款，避免仲裁与退款流程冲突
            if (hasActiveOrderDispute(orderId)) {
                throw new BusinessException("订单存在进行中纠纷，暂不可申请退款");
            }
            validateOrderAmounts(orderInfo);
            MoneyValidator.requireValid(dto.getAmount(), false, "退款金额");
            if (dto.getAmount().compareTo(orderInfo.getTotalAmount()) != 0) {
                throw new BusinessException("当前仅支持全额退款，退款金额须等于实付金额");
            }
            orderInfo.setRefundStatus(RefundStatus.PENDING.getCode());
            orderInfo.setRefundReason(dto.getReason());
            orderInfo.setRefundAmount(dto.getAmount());
            orderInfo.setRefundApplyTime(LocalDateTime.now());
            boolean canFastRefund = OrderStatus.PENDING_DELIVERY.getCode().equals(orderInfo.getOrderStatus())
                    && creditScoreService.getCreditScore(userId) >= 100;
            if (canFastRefund) {
                orderInfo.setRefundFastTrack(1);
                finalizeRefund(orderInfo, 0L, "信用优秀极速退款", true, true);
                creditScoreService.adjustByFastRefund(userId);
                noticeService.sendNotice(
                        orderInfo.getBuyerId(),
                        "极速退款成功",
                        "订单 [" + orderInfo.getOrderNo() + "] 已为您极速退款到账（信用分-1）。",
                        NoticeType.TRADE.getCode(),
                        orderId
                );
                noticeService.sendNotice(
                        orderInfo.getSellerId(),
                        "订单已极速退款",
                        "订单 [" + orderInfo.getOrderNo() + "] 因买家信用优秀触发极速退款，订单已取消。",
                        NoticeType.TRADE.getCode(),
                        orderId
                );
                return;
            }
            orderInfo.setRefundFastTrack(0);
            boolean beforeDelivery = OrderStatus.PENDING_DELIVERY.getCode().equals(orderInfo.getOrderStatus());
            orderInfo.setRefundDeadline(beforeDelivery ? LocalDateTime.now().plusHours(24) : null);
            orderInfoMapper.updateById(orderInfo);

            noticeService.sendNotice(
                    orderInfo.getSellerId(),
                    "收到退款申请",
                    "订单 [" + orderInfo.getOrderNo() + "] 买家申请退款，"
                            + (beforeDelivery ? "请在24小时内处理。" : "请协商退货并处理，已交付订单不会超时自动退款。"),
                    NoticeType.TRADE.getCode(),
                    orderId
            );
            noticeService.sendNotice(
                    orderInfo.getBuyerId(),
                    "退款申请已提交",
                    beforeDelivery ? "您的退款申请已提交，卖家需在24小时内处理，超时将自动退款。"
                            : "您的退款申请已提交，请与卖家协商退货；已交付订单需卖家同意或平台裁定后退款。",
                    NoticeType.TRADE.getCode(),
                    orderId
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("申请退款过程中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processRefund(Long orderId, Long userId, RefundProcessDTO dto) {
        String lockKey = "order:lock:lifecycle:" + orderId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException("系统繁忙，请稍后重试");
            }

            OrderInfo orderInfo = orderInfoMapper.selectByIdForUpdate(orderId);
            if (orderInfo == null) {
                throw new BusinessException("订单不存在");
            }

            if (!orderInfo.getSellerId().equals(userId)) {
                throw new BusinessException("仅卖家可处理退款");
            }

            if (!RefundStatus.PENDING.getCode().equals(orderInfo.getRefundStatus())) {
                throw new BusinessException("当前退款单不可处理");
            }

            String action = dto.getAction() == null ? "" : dto.getAction().trim().toLowerCase();
            if ("approve".equals(action)) {
                finalizeRefund(orderInfo, userId, dto.getRemark(), false, true);
                noticeService.sendNotice(
                        orderInfo.getBuyerId(),
                        "退款已通过",
                        "订单 [" + orderInfo.getOrderNo() + "] 卖家已同意退款，金额已退回。",
                        NoticeType.TRADE.getCode(),
                        orderId
                );
                return;
            }

            if ("reject".equals(action)) {
                orderInfo.setRefundStatus(RefundStatus.REJECTED.getCode());
                orderInfo.setRefundProcessTime(LocalDateTime.now());
                orderInfo.setRefundProcessorId(userId);
                orderInfo.setRefundProcessRemark(dto.getRemark());
                orderInfoMapper.updateById(orderInfo);

                noticeService.sendNotice(
                        orderInfo.getBuyerId(),
                        "退款申请被拒绝",
                        "订单 [" + orderInfo.getOrderNo() + "] 退款申请被卖家拒绝，可发起纠纷继续申诉。",
                        NoticeType.TRADE.getCode(),
                        orderId
                );
                return;
            }

            throw new BusinessException("无效的退款处理动作");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("处理退款过程中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Scheduled(cron = "0 */10 * * * ?")
    public void autoProcessRefunds() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getRefundStatus, RefundStatus.PENDING.getCode())
                .eq(OrderInfo::getOrderStatus, OrderStatus.PENDING_DELIVERY.getCode())
                .le(OrderInfo::getRefundDeadline, now);

        List<OrderInfo> timeoutOrders = orderInfoMapper.selectList(wrapper);
        if (timeoutOrders.isEmpty()) {
            return;
        }

        for (OrderInfo order : timeoutOrders) {
            String lockKey = "order:lock:lifecycle:" + order.getOrderId();
            RLock lock = redissonClient.getLock(lockKey);
            boolean acquired = false;
            try {
                acquired = lock.tryLock(0, 10, TimeUnit.SECONDS);
                if (!acquired) {
                    log.debug("自动退款跳过，订单正在处理: orderId={}", order.getOrderId());
                    continue;
                }
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    OrderInfo latestOrder = orderInfoMapper.selectByIdForUpdate(order.getOrderId());
                    if (latestOrder == null) {
                        return;
                    }
                    boolean stillPendingRefund = RefundStatus.PENDING.getCode().equals(latestOrder.getRefundStatus());
                    boolean stillBeforeDelivery = OrderStatus.PENDING_DELIVERY.getCode().equals(latestOrder.getOrderStatus());
                    boolean timeoutReached = latestOrder.getRefundDeadline() != null
                            && !latestOrder.getRefundDeadline().isAfter(now);
                    if (!stillPendingRefund || !stillBeforeDelivery || !timeoutReached) {
                        return;
                    }

                    finalizeRefund(latestOrder, 0L, "卖家超时未处理，系统自动退款", false, true);
                    noticeService.sendNotice(
                            latestOrder.getBuyerId(),
                            "退款已自动通过",
                            "订单 [" + latestOrder.getOrderNo() + "] 卖家超时未处理，系统已自动退款。",
                            NoticeType.TRADE.getCode(),
                            latestOrder.getOrderId()
                    );
                    noticeService.sendNotice(
                            latestOrder.getSellerId(),
                            "退款已自动处理",
                            "订单 [" + latestOrder.getOrderNo() + "] 因超时未处理，系统已自动退款。",
                            NoticeType.TRADE.getCode(),
                            latestOrder.getOrderId()
                    );
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("自动处理退款被中断，orderId={}", order.getOrderId());
            } catch (Exception e) {
                log.error("自动处理退款失败，orderId={}", order.getOrderId(), e);
            } finally {
                if (acquired && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * 自动确认收货任务
     * 每天凌晨执行，查询发货超过7天的订单，自动确认收货
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void autoConfirmReceipt() {
        log.info("开始执行订单自动确认收货任务...");

        // 查询发货超过7天的订单（状态为2-待收货）
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getOrderStatus, OrderStatus.PENDING_RECEIVE.getCode())
                .ne(OrderInfo::getRefundStatus, RefundStatus.PENDING.getCode())
                .le(OrderInfo::getDeliveryTime, sevenDaysAgo);

        List<OrderInfo> orders = orderInfoMapper.selectList(wrapper);
        log.info("发现{}个超时未确认订单", orders.size());

        for (OrderInfo order : orders) {
            String lockKey = "order:lock:lifecycle:" + order.getOrderId();
            RLock lock = redissonClient.getLock(lockKey);
            boolean acquired = false;
            try {
                acquired = lock.tryLock(0, 10, TimeUnit.SECONDS);
                if (!acquired) {
                    log.debug("自动确认跳过，订单正在处理: orderId={}", order.getOrderId());
                    continue;
                }
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    OrderInfo latestOrder = orderInfoMapper.selectByIdForUpdate(order.getOrderId());
                    if (latestOrder == null) {
                        return;
                    }
                    boolean canAutoConfirm = OrderStatus.PENDING_RECEIVE.getCode().equals(latestOrder.getOrderStatus())
                            && !RefundStatus.PENDING.getCode().equals(latestOrder.getRefundStatus())
                            && latestOrder.getDeliveryTime() != null
                            && !latestOrder.getDeliveryTime().isAfter(sevenDaysAgo);
                    if (!canAutoConfirm) {
                        return;
                    }

                    if (hasActiveOrderDispute(latestOrder.getOrderId())) {
                        log.info("订单存在进行中纠纷，跳过自动确认收货: orderId={}", latestOrder.getOrderId());
                        return;
                    }

                    // 资金结算：转入卖家账户
                    validateOrderAmounts(latestOrder);
                    if (userInfoMapper.creditBalance(latestOrder.getSellerId(), latestOrder.getTotalAmount()) != 1) {
                        throw new BusinessException("卖家账户不存在，无法结算");
                    }

                    // 更新订单状态
                    latestOrder.setOrderStatus(OrderStatus.COMPLETED.getCode()); // 已完成
                    latestOrder.setReceiveTime(LocalDateTime.now());
                    orderInfoMapper.updateById(latestOrder);

                    log.info("订单{}自动确认收货成功", latestOrder.getOrderNo());

                    // 发送通知
                    noticeService.sendNotice(
                            latestOrder.getBuyerId(),
                            "自动收货通知",
                            "您的订单 [" + latestOrder.getOrderNo() + "] 因超时未确认已自动收货。",
                            NoticeType.TRADE.getCode()
                    );
                    noticeService.sendNotice(
                            latestOrder.getSellerId(),
                            "交易完成",
                            "订单 [" + latestOrder.getOrderNo() + "] 已自动确认收货，资金已入账。",
                            NoticeType.TRADE.getCode()
                    );
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("自动确认收货被中断，orderId={}", order.getOrderId());
            } catch (Exception e) {
                log.error("订单{}自动确认收货失败", order.getOrderNo(), e);
            } finally {
                if (acquired && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }

        log.info("订单自动确认收货任务执行完毕");
    }

    private void finalizeRefund(
            OrderInfo orderInfo,
            Long processorId,
            String processRemark,
            boolean fastTrack,
            boolean closeWithCancelStatus
    ) {
        if (!OrderStatus.PENDING_DELIVERY.getCode().equals(orderInfo.getOrderStatus())
                && !OrderStatus.PENDING_RECEIVE.getCode().equals(orderInfo.getOrderStatus())) {
            throw new BusinessException("当前订单状态不可退款");
        }
        validateOrderAmounts(orderInfo);
        BigDecimal refundAmount = orderInfo.getRefundAmount() == null
                ? orderInfo.getTotalAmount()
                : orderInfo.getRefundAmount();
        MoneyValidator.requireValid(refundAmount, false, "退款金额");
        if (refundAmount.compareTo(orderInfo.getTotalAmount()) != 0) {
            throw new BusinessException("当前仅支持全额退款，请卖家拒绝原部分退款申请后重新申请");
        }
        if (userInfoMapper.creditBalance(orderInfo.getBuyerId(), refundAmount) != 1) {
            throw new BusinessException("买家不存在，无法退款");
        }

        GoodsInfo goodsInfo = goodsInfoMapper.selectByIdForUpdate(orderInfo.getProductId());
        if (goodsInfo != null) {
            boolean neverDelivered = OrderStatus.PENDING_DELIVERY.getCode().equals(orderInfo.getOrderStatus());
            boolean approvedGoods = ReviewStatus.AI_PASSED.getCode().equals(goodsInfo.getReviewStatus())
                    || ReviewStatus.MANUAL_PASSED.getCode().equals(goodsInfo.getReviewStatus());
            // 已交付物品须先确认退回；管理员下架也不能被退款流程覆盖。
            boolean canRelist = neverDelivered && approvedGoods
                    && TradeStatus.SOLD.getCode().equals(goodsInfo.getTradeStatus());
            goodsInfo.setTradeStatus(canRelist ? TradeStatus.ON_SALE.getCode() : TradeStatus.OFF_SHELF.getCode());
            goodsInfoMapper.updateById(goodsInfo);
            notifyGoodsStatusChanged(orderInfo.getProductId());
        }

        orderInfo.setRefundStatus(RefundStatus.APPROVED.getCode());
        orderInfo.setRefundProcessTime(LocalDateTime.now());
        orderInfo.setRefundProcessorId(processorId);
        orderInfo.setRefundProcessRemark(processRemark);
        orderInfo.setRefundFastTrack(fastTrack ? 1 : 0);
        orderInfo.setRefundDeadline(null);
        if (closeWithCancelStatus) {
            orderInfo.setOrderStatus(OrderStatus.CANCELLED.getCode());
            orderInfo.setCancelTime(LocalDateTime.now());
        }
        orderInfoMapper.updateById(orderInfo);

        log.info("订单{}退款完成，金额：{}", orderInfo.getOrderNo(), refundAmount);
    }

    private void validateOrderAmounts(OrderInfo order) {
        MoneyValidator.requireValid(order.getOrderAmount(), false, "商品金额");
        BigDecimal deliveryFee = order.getDeliveryFee() == null ? BigDecimal.ZERO : order.getDeliveryFee();
        MoneyValidator.requireValid(deliveryFee, true, "运费");
        MoneyValidator.requireValid(order.getTotalAmount(), false, "订单总额");
        if (order.getOrderAmount().add(deliveryFee).compareTo(order.getTotalAmount()) != 0) {
            throw new BusinessException("订单金额不一致，请联系平台处理");
        }
        if (Integer.valueOf(0).equals(order.getTradeType()) && deliveryFee.signum() != 0) {
            throw new BusinessException("面交订单不应收取运费，请联系平台处理");
        }
    }

    private int resolveTradeType(Integer offeredType, Integer selectedType) {
        if (offeredType == null || offeredType < 0 || offeredType > 2) {
            throw new BusinessException("商品交付方式缺失或不合法，请联系卖家更新");
        }
        if (offeredType == 2) {
            if (selectedType == null || selectedType < 0 || selectedType > 1) {
                throw new BusinessException("请明确选择面交或邮寄");
            }
            return selectedType;
        }
        if (selectedType != null && !selectedType.equals(offeredType)) {
            throw new BusinessException("商品不支持所选交付方式");
        }
        return offeredType;
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        return "ORD" + IdUtil.getSnowflakeNextIdStr();
    }

    private void notifyGoodsStatusChanged(Long productId) {
        if (productId == null) {
            return;
        }
        Runnable syncTask = () -> {
            try {
                rocketMQTemplate.convertAndSend(
                        RocketMQConfig.GOODS_SYNC_TOPIC,
                        GoodsSyncMessage.updateMessage(productId)
                );
            } catch (Exception e) {
                log.error("发送商品状态同步消息失败: productId={}", productId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncTask.run();
                }
            });
            return;
        }
        syncTask.run();
    }

    /**
     * 转换为VO
     */
    private OrderVO convertToVO(OrderInfo orderInfo) {
        OrderVO vo = BeanUtil.copyProperties(orderInfo, OrderVO.class);

        // 查询买家信息
        UserInfo buyer = userInfoMapper.selectById(orderInfo.getBuyerId());
        if (buyer != null) {
            vo.setBuyerName(buyer.getNickName());
            vo.setBuyerAvatar(buyer.getImageUrl());
        }

        // 查询卖家信息
        UserInfo seller = userInfoMapper.selectById(orderInfo.getSellerId());
        if (seller != null) {
            vo.setSellerName(seller.getNickName());
            vo.setSellerAvatar(seller.getImageUrl());
        }

        // 查询商品信息
        GoodsInfo goodsInfo = goodsInfoMapper.selectById(orderInfo.getProductId());
        if (goodsInfo != null) {
            vo.setProductTitle(goodsInfo.getTitle());
            vo.setProductImage(goodsInfo.getImage());
        }

        fillActiveDisputeInfo(
                vo,
                buildActiveOrderDisputeMap(List.of(orderInfo.getOrderId()))
        );
        fillLatestClosedDisputeInfo(
                vo,
                buildLatestClosedOrderDisputeMap(List.of(orderInfo.getOrderId()))
        );

        return vo;
    }

    /**
     * 转换为VO列表
     */
    private List<OrderVO> convertToVOList(List<OrderInfo> orderList) {
        if (orderList.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量查询用户信息
        List<Long> userIds = orderList.stream()
                .flatMap(order -> List.of(order.getBuyerId(), order.getSellerId()).stream())
                .distinct()
                .collect(Collectors.toList());
        List<UserInfo> users = userInfoMapper.selectBatchIds(userIds);
        Map<Long, UserInfo> userMap = users.stream()
                .collect(Collectors.toMap(UserInfo::getUserId, user -> user));

        // 批量查询商品信息
        List<Long> productIds = orderList.stream()
                .map(OrderInfo::getProductId)
                .distinct()
                .collect(Collectors.toList());
        List<GoodsInfo> goodsList = goodsInfoMapper.selectBatchIds(productIds);
        Map<Long, GoodsInfo> goodsMap = goodsList.stream()
                .collect(Collectors.toMap(GoodsInfo::getProductId, goods -> goods));
        List<Long> orderIds = orderList.stream()
                .map(OrderInfo::getOrderId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, DisputeRecord> activeDisputeMap = buildActiveOrderDisputeMap(orderIds);
        Map<Long, DisputeRecord> latestClosedDisputeMap = buildLatestClosedOrderDisputeMap(orderIds);

        // 转换为VO
        List<OrderVO> voList = new ArrayList<>();
        for (OrderInfo orderInfo : orderList) {
            OrderVO vo = BeanUtil.copyProperties(orderInfo, OrderVO.class);

            // 设置买家信息
            UserInfo buyer = userMap.get(orderInfo.getBuyerId());
            if (buyer != null) {
                vo.setBuyerName(buyer.getNickName());
                vo.setBuyerAvatar(buyer.getImageUrl());
            }

            // 设置卖家信息
            UserInfo seller = userMap.get(orderInfo.getSellerId());
            if (seller != null) {
                vo.setSellerName(seller.getNickName());
                vo.setSellerAvatar(seller.getImageUrl());
            }

            // 设置商品信息
            GoodsInfo goodsInfo = goodsMap.get(orderInfo.getProductId());
            if (goodsInfo != null) {
                vo.setProductTitle(goodsInfo.getTitle());
                vo.setProductImage(goodsInfo.getImage());
            }

            fillActiveDisputeInfo(vo, activeDisputeMap);
            fillLatestClosedDisputeInfo(vo, latestClosedDisputeMap);

            voList.add(vo);
        }

        return voList;
    }

    private Map<Long, DisputeRecord> buildActiveOrderDisputeMap(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<DisputeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DisputeRecord::getContentId, orderIds)
                .eq(DisputeRecord::getTargetType, DisputeTargetType.ORDER.getCode())
                .in(DisputeRecord::getHandleStatus, DisputeStatus.PENDING.getCode(), DisputeStatus.PROCESSING.getCode())
                .orderByDesc(DisputeRecord::getCreateTime);
        List<DisputeRecord> records = disputeRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return Map.of();
        }
        Map<Long, DisputeRecord> map = new java.util.HashMap<>();
        for (DisputeRecord record : records) {
            map.putIfAbsent(record.getContentId(), record);
        }
        return map;
    }

    private Map<Long, DisputeRecord> buildLatestClosedOrderDisputeMap(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<DisputeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DisputeRecord::getContentId, orderIds)
                .eq(DisputeRecord::getTargetType, DisputeTargetType.ORDER.getCode())
                .in(DisputeRecord::getHandleStatus,
                        DisputeStatus.RESOLVED.getCode(),
                        DisputeStatus.REJECTED.getCode())
                .orderByDesc(DisputeRecord::getCreateTime);
        List<DisputeRecord> records = disputeRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return Map.of();
        }
        Map<Long, DisputeRecord> map = new java.util.HashMap<>();
        for (DisputeRecord record : records) {
            map.putIfAbsent(record.getContentId(), record);
        }
        return map;
    }

    private void fillActiveDisputeInfo(OrderVO vo, Map<Long, DisputeRecord> activeDisputeMap) {
        DisputeRecord activeDispute = activeDisputeMap.get(vo.getOrderId());
        if (activeDispute == null) {
            vo.setHasActiveDispute(false);
            vo.setActiveDisputeId(null);
            vo.setActiveDisputeStatus(null);
            return;
        }
        vo.setHasActiveDispute(true);
        vo.setActiveDisputeId(activeDispute.getRecordId());
        vo.setActiveDisputeStatus(activeDispute.getHandleStatus());
    }

    private void fillLatestClosedDisputeInfo(OrderVO vo, Map<Long, DisputeRecord> latestClosedDisputeMap) {
        DisputeRecord latestClosedDispute = latestClosedDisputeMap.get(vo.getOrderId());
        if (latestClosedDispute == null) {
            vo.setLatestClosedDisputeId(null);
            vo.setLatestClosedDisputeStatus(null);
            vo.setLatestClosedDisputeResult(null);
            vo.setLatestClosedDisputeRefundAmount(null);
            vo.setLatestClosedDisputeCreditPenalty(null);
            return;
        }
        vo.setLatestClosedDisputeId(latestClosedDispute.getRecordId());
        vo.setLatestClosedDisputeStatus(latestClosedDispute.getHandleStatus());
        vo.setLatestClosedDisputeResult(latestClosedDispute.getHandleResult());
        vo.setLatestClosedDisputeRefundAmount(latestClosedDispute.getResolvedRefundAmount());
        vo.setLatestClosedDisputeCreditPenalty(latestClosedDispute.getResolvedCreditPenalty());
    }

    private boolean hasActiveOrderDispute(Long orderId) {
        if (orderId == null) {
            return false;
        }
        LambdaQueryWrapper<DisputeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DisputeRecord::getContentId, orderId)
                .eq(DisputeRecord::getTargetType, DisputeTargetType.ORDER.getCode())
                .in(DisputeRecord::getHandleStatus, DisputeStatus.PENDING.getCode(), DisputeStatus.PROCESSING.getCode());
        return disputeRecordMapper.selectCount(wrapper) > 0;
    }
}
