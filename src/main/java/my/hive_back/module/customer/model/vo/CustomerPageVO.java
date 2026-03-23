package my.hive_back.module.customer.model.vo;

import lombok.Data;
import my.hive_back.module.customer.model.entity.Customer;

import java.util.List;

@Data
public class CustomerPageVO {
    private Long id;
    private String customerName;
    private Integer customerType;
    private String constructionArea;
    private Integer projectCount;
    private List<String> projectNames;

}
