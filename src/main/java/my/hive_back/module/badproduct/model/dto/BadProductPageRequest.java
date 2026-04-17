package my.hive_back.module.badproduct.model.dto;

import lombok.Data;
/**
 * BadProductPageRequest 属于小程序后端坏品模块，定义入参结构。
 */
@Data
public class BadProductPageRequest {
    private Integer pageNum = 1;
    private Integer pageSize = 50;
    private String status;
    private String type;
    private String date;
}
