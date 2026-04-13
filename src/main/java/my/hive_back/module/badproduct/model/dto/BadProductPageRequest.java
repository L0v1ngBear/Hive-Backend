package my.hive_back.module.badproduct.model.dto;

import lombok.Data;

@Data
public class BadProductPageRequest {
    private Integer pageNum = 1;
    private Integer pageSize = 50;
    private String status;
    private String type;
    private String date;
}