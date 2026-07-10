package my.hive_back.module.order.model.vo;

import lombok.Data;
import my.hive_back.module.order.model.entity.SalesOrderDetail;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SalesOrderVO 属于小程序后端订单模块，定义出参结构。
 */
@Data
public class SalesOrderVO {

    private String orderId;

    /**
     * 订单状态：pending_confirm, pending_material, producing, pending_ship, shipped, completed
     */
    private String status;

    private String customerName;

    private String customerPhone;

    /**
     * 项目名称
     */
    private String projectName;

    private String brandName;

    private String orderCategory;

    private String goodsDesc;

    private Integer totalQuantity;

    private String deliveryDate;

    /**
     * 是否同步创建生产订单 0-否 1-是
     */
    private Integer createProductionOrder;

    private String expressCompany;

    private String expressNo;

    private String remark;

    private String attachmentName;

    private String attachmentUrl;

    private Long attachmentSize;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 是否开票 0-否 1-是
     */
    private Integer isInvoice;

    /**
     * 核心修改：商品明细列表
     */
    private List<OrderItemVO> items;

    /**
     * 销售订单状态流转记录，详情页用于展示时间轴。
     */
    private List<SalesOrderStatusLogVO> logs;

    /**
     * 商品明细内部类
     */
    @Data
    public static class OrderItemVO {
        /**
         * 商品型号 (如: T800-210)
         */
        private String modelCode;

        /**
         * 数量
         */
        private BigDecimal quantity;

        /**
         * 克重
         */
        private String weight;

        /**
         * 规格/幅宽
         */
        private Float spec;
    }
}
