package my.hive_back.module.customer.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class CustomerAddRequest {

    private String customerName;

    private Integer customerType;

    private String constructionArea;

    private List<contacts> contacts;

    private List<projects> projects;

    public static class contacts {
        private String contactName;
        private String contactPhone;
    }

    public static class projects {
        private String projectName;
    }
}
