package my.hive_back.api.customer;

import my.hive_back.module.tenant.TenantFeatureEnum;
import my.hive_back.module.sys.model.enums.PermissionCodeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive.common.annotation.RequirePermission;
import my.hive.common.dto.PageResult;
import my.hive.common.dto.Result;
import my.hive_back.common.tenant.RequireTenantFeature;
import my.hive_back.module.customer.mapper.CustomerProjectMapper;
import my.hive_back.module.customer.model.dto.CustomerAddRequest;
import my.hive_back.module.customer.model.dto.CustomerPageRequest;
import my.hive_back.module.customer.model.entity.Customer;
import my.hive_back.module.customer.model.entity.CustomerProject;
import my.hive_back.module.customer.model.vo.CustomerDetailVO;
import my.hive_back.module.customer.model.vo.CustomerPageVO;
import my.hive_back.module.customer.service.CustomerService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
/**
 * CustomerController handles customer requests for the mini-program backend and delegates to services.
 */
@RestController
@RequestMapping("/customer")
@RequireTenantFeature(TenantFeatureEnum.CODE_MODULE_CUSTOMER)
@Validated
public class CustomerController {

    @Resource
    private CustomerService customerService;

    @Resource
    private CustomerProjectMapper customerProjectMapper;

    @PostMapping("/add")
    @RequirePermission(value = PermissionCodeEnum.CODE_CUSTOMER_ADD, message = "您没有权限新增客户")
    public Result<Void> addCustomer(@RequestBody CustomerAddRequest request) {
        customerService.addCustomer(request);
        return Result.success(null);
    }

    @GetMapping("/page")
    @RequirePermission(value = PermissionCodeEnum.CODE_CUSTOMER_PAGE, message = "您没有权限查看客户列表")
    public Result<PageResult<CustomerPageVO>> getCustomerPage(@Valid CustomerPageRequest request) {
        Page<Customer> page = Optional.ofNullable(customerService.pageSearchCustomer(request))
                .orElse(new Page<>()); // 若返回null，初始化空分页对象
        // 4. 组装 VO (统计项目数和最新合作项目)
        List<CustomerPageVO> voList = page.getRecords().stream().map(customer -> {
            CustomerPageVO vo = new CustomerPageVO();
            BeanUtils.copyProperties(customer, vo);
            // 注意：如果你 DTO 里叫 companyName，这里就不用转了，按实际情况来

            // 查询该客户下的所有项目 (倒序排，最新的在前面)
            List<CustomerProject> projects = customerProjectMapper.selectList(
                    new LambdaQueryWrapper<CustomerProject>()
                            .eq(CustomerProject::getCustomerId, customer.getId())
                            .orderByDesc(CustomerProject::getId)
            );

            // 组装聚合字段
            vo.setProjectCount(projects.size());
            vo.setProjectNames(projects.stream().map(CustomerProject::getProjectName).collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList());

        // 5. 封装为你统一的 PageResultVO 返回
        PageResult<CustomerPageVO> result = new PageResult<>();
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setData(voList);

        return Result.success(result);
    }

    @GetMapping("/detail/{id}")
    @RequirePermission(value = PermissionCodeEnum.CODE_CUSTOMER_DETAIL, message = "您没有权限查看客户详情")
    public Result<CustomerDetailVO> getCustomer(@PathVariable Long id) {
        CustomerDetailVO customerDetailVO = customerService.getCustomer(id);
        return Result.success(customerDetailVO);
    }
}
