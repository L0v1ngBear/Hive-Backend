package my.hive_back.module.installation.service;

import jakarta.annotation.Resource;
import my.hive_back.module.installation.mapper.InstallationTaskMapper;
import my.hive_back.module.installation.model.entity.InstallationTask;
import my.hive_back.module.order.OrderStatusEnum;
import my.hive_back.module.order.model.entity.SalesOrder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class InstallationTaskSyncService {

    private static final String STATUS_PRODUCTION_COMPLETED = "production_completed";

    @Resource
    private InstallationTaskMapper installationTaskMapper;

    public void createOrSyncFromCompletedOrder(SalesOrder order) {
        if (order == null || !OrderStatusEnum.COMPLETED.getCode().equals(order.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        InstallationTask task = new InstallationTask();
        task.setTenantCode(order.getTenantCode());
        task.setOrderId(order.getOrderId());
        task.setOrderStatus(order.getStatus());
        task.setInstallationStatus(STATUS_PRODUCTION_COMPLETED);
        task.setCustomerName(order.getCustomerName());
        task.setCustomerPhone(order.getCustomerPhone());
        task.setProjectName(order.getProjectName());
        task.setBrandName(order.getBrandName());
        task.setOrderCategory(order.getOrderCategory());
        task.setGoodsDesc(order.getGoodsDesc());
        task.setTotalQuantity(order.getTotalQuantity());
        task.setDeliveryDate(order.getDeliveryDate());
        task.setExpressCompany(order.getExpressCompany());
        task.setExpressNo(order.getExpressNo());
        task.setIsInvoice(order.getIsInvoice());
        task.setCreator(order.getCreator());
        task.setRemark(order.getRemark());
        task.setOrderAttachmentName(order.getAttachmentName());
        task.setOrderAttachmentUrl(order.getAttachmentUrl());
        task.setOrderAttachmentSize(order.getAttachmentSize());
        task.setOrderCompletedTime(now);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        installationTaskMapper.upsertFromCompletedOrder(task);
    }
}
