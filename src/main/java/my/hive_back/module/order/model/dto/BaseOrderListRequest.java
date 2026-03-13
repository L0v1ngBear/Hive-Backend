package my.hive_back.module.order.model.dto;

import lombok.Data;

@Data
public class BaseOrderListRequest {
    private String status;
    private String keyWord;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
