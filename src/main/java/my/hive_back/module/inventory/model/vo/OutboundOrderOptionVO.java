package my.hive_back.module.inventory.model.vo;

import lombok.Data;

/**
 * 出库选择业务单选项，供小程序扫码出库前搜索销售订单。
 */
@Data
public class OutboundOrderOptionVO {

    private String orderNo;

    private String customerName;

    private String projectName;
}
