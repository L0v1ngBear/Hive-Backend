package my.hive_back.module.home.model.vo;

import lombok.Data;
import my.hive_back.module.todo.model.vo.TodoItemVO;

import java.util.List;
/**
 * HomeSummaryVO 属于小程序后端首页模块，定义出参结构。
 */
@Data
public class HomeSummaryVO {

    private TenantInfo tenantInfo;

    private UserInfo userInfo;

    private FunctionEnable functionEnable;

    private Integer todoCount;

    private List<TodoItemVO> todoList;

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
}
