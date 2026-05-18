package my.hive_back.api.wechat;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive.common.annotation.CollectLog;
import my.hive.common.dto.Result;
import my.hive_back.module.wechat.model.dto.WechatSubscribeRegisterRequest;
import my.hive_back.module.wechat.model.vo.WechatSubscribeConfigVO;
import my.hive_back.module.wechat.service.WechatSubscribeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信订阅消息控制器，承接小程序授权登记和模板配置查询。
 */
@RestController
@RequestMapping("/wechat/subscribe")
public class WechatSubscribeController {

    @Resource
    private WechatSubscribeService wechatSubscribeService;

    @GetMapping("/config")
    public Result<WechatSubscribeConfigVO> config() {
        return Result.success(wechatSubscribeService.getConfig());
    }

    @PostMapping("/register")
    @CollectLog(module = "wechat_subscribe", action = "register", bizType = "wechat_subscribe", description = "小程序登记微信订阅消息授权")
    public Result<Void> register(@Valid @RequestBody WechatSubscribeRegisterRequest request) {
        wechatSubscribeService.register(request);
        return Result.success(null);
    }
}
