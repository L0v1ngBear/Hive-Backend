package my.hive_back.module.customer.model.vo;

import lombok.Data;
import my.hive_back.module.customer.model.entity.Customer;

import java.util.List;
/**
 * CustomerPageVO 属于小程序后端客户模块，定义出参结构。
 */
@Data
public class CustomerPageVO {
    private Long id;
    private String customerName;
    private Integer customerType;
    private String constructionArea;
    private Integer projectCount;
    private List<String> projectNames;

}
