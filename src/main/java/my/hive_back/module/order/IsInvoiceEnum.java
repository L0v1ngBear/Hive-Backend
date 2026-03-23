package my.hive_back.module.order;

import lombok.Getter;

@Getter
public enum IsInvoiceEnum {
    YES(1),
    NO(0);

    private int code;

    private IsInvoiceEnum(int code) {
        this.code = code;
    }
}
