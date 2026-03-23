package my.hive_back.module.customer.model.dto;

import lombok.Data;

@Data
public class CustomerPageRequest {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    // 对应顶部的搜索框
    private String keyword;
}
