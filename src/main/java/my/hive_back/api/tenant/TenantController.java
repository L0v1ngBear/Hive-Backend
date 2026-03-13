package my.hive_back.api.tenant;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import my.hive_back.common.dto.PageResultVO;
import my.hive_back.common.dto.ResultDTO;
import my.hive_back.module.tenant.model.dto.TenantInfoPageRequest;
import my.hive_back.module.tenant.model.entity.Tenant;
import my.hive_back.module.tenant.model.vo.TenantVO;
import my.hive_back.module.tenant.service.TenantService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.validation.Valid;

import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/tenant")
public class TenantController {

    @Resource
    private TenantService tenantService;

    @GetMapping("/page-search")
    public ResultDTO<PageResultVO<TenantVO>> pageSearchTenant(@Valid @RequestBody TenantInfoPageRequest searchDTO) {

        Page<Tenant> tenantPage = Optional.ofNullable(tenantService.pageSearchTenant(searchDTO))
                .orElse(new Page<>()); // 若返回null，初始化空分页对象

        PageResultVO<TenantVO> tenantVoPage = new PageResultVO<>() {{
            // 复制分页核心参数（初始化块简化setter调用）
            setCurrent(tenantPage.getCurrent());
            setSize(tenantPage.getSize());
            setTotal(tenantPage.getTotal());
            setPages(tenantPage.getPages());
            setData(tenantPage.getRecords().stream()
                    .map(TenantVO::new)
                    .collect(Collectors.toList()));
        }};

        return ResultDTO.success(tenantVoPage);
    }
}
