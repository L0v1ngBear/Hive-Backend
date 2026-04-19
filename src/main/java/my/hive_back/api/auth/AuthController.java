package my.hive_back.api.auth;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import my.hive.common.dto.Result;
import my.hive_back.module.auth.model.dto.LoginRequest;
import my.hive_back.module.auth.model.vo.LoginVO;
import my.hive_back.module.auth.service.AuthService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * AuthController 是小程序后端认证入口控制类，负责接收请求并调用对应服务。
 */
@RestController
/**
 * AuthController handles authentication requests for the mini-program backend and delegates to services.
 */
@RequestMapping("/auth")
@Validated
public class AuthController {

    @Resource
    private AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return Result.success(authService.login(request, getClientIp(servletRequest)));
    }

    @GetMapping("/me")
    public Result<LoginVO> currentUser() {
        return Result.success(authService.currentUser());
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
