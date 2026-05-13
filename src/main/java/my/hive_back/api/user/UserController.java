package my.hive_back.api.user;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive.common.dto.Result;
import my.hive_back.module.auth.model.vo.LoginVO;
import my.hive_back.module.user.model.dto.JoinOrganizationRequest;
import my.hive_back.module.user.service.UserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序用户入口。
 */
@RestController
@RequestMapping("/user")
@Validated
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/join-organization")
    public Result<LoginVO> joinOrganization(@Valid @RequestBody JoinOrganizationRequest request) {
        return Result.success(userService.joinOrganization(request));
    }
}
