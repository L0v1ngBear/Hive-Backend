package my.hive_back.api.home;

import jakarta.annotation.Resource;
import my.hive.common.dto.Result;
import my.hive_back.module.home.model.vo.HomeSummaryVO;
import my.hive_back.module.home.service.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * HomeController handles home requests for the mini-program backend and delegates to services.
 */
@RestController
@RequestMapping("/home")
public class HomeController {

    @Resource
    private HomeService homeService;

    @GetMapping("/summary")
    public Result<HomeSummaryVO> summary() {
        return Result.success(homeService.getSummary());
    }
}
