package my.hive_back.module.order.model.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalesOrderVO {
    private String orderId;
    private String status;
    private String customerName;
    private String goodsDesc;
    private BigDecimal totalAmount;
    private Integer totalQuantity;
    private String deliveryDate;
    private String expressCompany;
    private String expressNo;
    private String createTime;
}
