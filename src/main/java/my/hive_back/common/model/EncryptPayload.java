package my.hive_back.common.model;

import lombok.Data;
/**
 * EncryptPayload 属于小程序后端通用能力层，定义通用模型。
 */
@Data
public class EncryptPayload {

    private String iv;

    private String ciphertext;

    private String mac;
}
