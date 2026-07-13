package my.hive_back.module.user;

import lombok.Getter;
/**
 * RoleLevelEnum 属于小程序后端用户模块，属于该领域的细分实现。
 */
@Getter
public enum RoleLevelEnum {
    NORMAL(0, "普通员工"),
    MANAGER(1, "部门主管"),
    ADMIN(2, "部门总监"),
    BOSS(3, "公司老板");

    private int level;
    private String desc;

    RoleLevelEnum(int level, String desc) {
        this.level = level;
        this.desc = desc;
    }
}
