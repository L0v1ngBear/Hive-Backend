package my.hive_back.module.order;

import lombok.Getter;
/**
 * IsInvoiceEnum 属于小程序后端订单模块，属于该领域的细分实现。
 */
@Getter
public enum IsInvoiceEnum {
    YES(1),
    NO(0);

    private int code;

    private IsInvoiceEnum(int code) {
        this.code = code;
    }
}
