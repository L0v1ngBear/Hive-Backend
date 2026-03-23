package my.hive_back.module.customer.model.vo;

import lombok.Data;
import my.hive_back.module.customer.model.entity.CustomerContact;
import my.hive_back.module.customer.model.entity.CustomerProject;

import java.util.List;

@Data
public class CustomerDetailVO {
    private Long id;
    private String companyName;
    private Integer customerType;
    private String constructionArea;

    // 包含一对多关联的子表数据
    private List<CustomerContact> contacts;
    private List<CustomerProject> projects;
}