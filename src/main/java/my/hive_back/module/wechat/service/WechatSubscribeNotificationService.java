package my.hive_back.module.wechat.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 微信订阅消息业务通知封装。
 *
 * <p>订阅消息属于增强能力，不能影响主业务提交：所有推送都在事务提交后执行，
 * 推送失败只记录日志，不回滚订单、审批等核心业务。</p>
 */
@Slf4j
@Service
public class WechatSubscribeNotificationService {

    @Resource
    private WechatSubscribeService wechatSubscribeService;

    public void sendTodoAfterCommit(Long userId, String title, String content, String pagePath) {
        sendTodoAfterCommit(userId, null, title, content, pagePath);
    }

    public void sendTodoAfterCommit(Long userId, String operatorName, String title, String content, String pagePath) {
        if (userId == null) {
            return;
        }
        Runnable task = () -> safeSendTodo(userId, operatorName, title, content, pagePath);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void safeSendTodo(Long userId, String operatorName, String title, String content, String pagePath) {
        try {
            wechatSubscribeService.sendTodoReminder(userId, operatorName, title, content, pagePath);
        } catch (Exception e) {
            log.warn("微信订阅消息发送失败，不影响主业务 userId={} title={} pagePath={}", userId, title, pagePath, e);
        }
    }
}
