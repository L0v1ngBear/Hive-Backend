package my.hive_back.module.customer.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CustomerAddRequest {

    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    @NotBlank(message = "客户类型不能为空")
    private Integer customerType;

    @NotBlank(message = "施工区域不能为空")
    private String constructionArea;

    private List<contacts> contacts;

    private List<projects> projects;

    @Data
    public static class contacts {
        private String contactName;
        private String contactPhone;
    }

    @Data
    public static class projects {
        private String projectName;
    }
}
