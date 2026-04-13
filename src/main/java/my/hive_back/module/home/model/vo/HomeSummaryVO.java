package my.hive_back.module.home.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class HomeSummaryVO {

    private TenantInfo tenantInfo;

    private UserInfo userInfo;

    private FunctionEnable functionEnable;

    private Integer todoCount;

    private List<TodoItem> todoList;

    @Data
    public static class TenantInfo {
        private String name;
        private String code;
    }

    @Data
    public static class UserInfo {
        private Long id;
        private String name;
        private String dept;
    }

    @Data
    public static class FunctionEnable {
        private Boolean attendance;
        private Boolean order;
        private Boolean salesOrder;
        private Boolean inventory;
        private Boolean approval;
        private Boolean notice;
        private Boolean file;
        private Boolean badProduct;
        private Boolean knowledge;
        private Boolean customer;
    }

    @Data
    public static class TodoItem {
        private String id;
        private String tag;
        private String content;
        private String time;
    }
}