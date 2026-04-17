package my.hive_back.module.customer.model.dto;

import lombok.Data;
/**
 * CustomerPageRequest 属于小程序后端客户模块，定义入参结构。
 */
@Data
public class CustomerPageRequest {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    // 对应顶部的搜索框
    private String keyword;
}
