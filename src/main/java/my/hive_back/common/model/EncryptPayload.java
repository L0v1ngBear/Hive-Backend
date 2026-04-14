package my.hive_back.common.model;

import lombok.Data;

@Data
public class EncryptPayload {

    private String iv;

    private String ciphertext;

    private String mac;
}
