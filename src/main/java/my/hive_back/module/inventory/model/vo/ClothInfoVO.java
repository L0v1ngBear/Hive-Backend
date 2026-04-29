package my.hive_back.module.inventory.model.vo;

import lombok.Data;
/**
 * ClothInfoVO 属于小程序后端库存模块，定义出参结构。
 */
@Data
public class ClothInfoVO {

    private String barcode;

    private String modelCode;

    private Float spec;

    /**
     * 当前需要打印到标签上的米数。
     * 入库时为本次入库米数；部分出库后为布匹剩余可用米数。
     */
    private Float meters;

    /**
     * 出库前本条码可用米数，方便小程序判断本次是否为部分出库。
     */
    private Float beforeMeters;

    /**
     * 本次实际出库米数。
     */
    private Float outMeters;

    /**
     * 布匹当前状态：0-在库，1-已全部出库，2-部分出库。
     */
    private Integer status;

    /**
     * 是否需要打印标签：首次入库、部分出库后剩余布匹都需要打印。
     */
    private Boolean needPrintLabel;

    /**
     * 打印触发原因，用于前端给仓库人员明确提示。
     */
    private String printReason;

    /**
     * 后端生成的打印任务号，前端打印成功或失败后回传该编号。
     */
    private String printTaskNo;
}
