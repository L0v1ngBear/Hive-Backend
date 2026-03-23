package my.hive_back.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.common.exception.BusinessException;
import my.hive_back.module.customer.mapper.CustomerContactMapper;
import my.hive_back.module.customer.mapper.CustomerMapper;
import my.hive_back.module.customer.mapper.CustomerProjectMapper;
import my.hive_back.module.customer.model.dto.CustomerAddRequest;
import my.hive_back.module.customer.model.dto.CustomerPageRequest;
import my.hive_back.module.customer.model.entity.Customer;
import my.hive_back.module.customer.model.entity.CustomerContact;
import my.hive_back.module.customer.model.entity.CustomerProject;
import my.hive_back.module.customer.model.vo.CustomerDetailVO;
import my.hive_back.module.tenant.model.entity.Tenant;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    @Resource
    private CustomerMapper customerMapper;

    @Resource
    private CustomerContactMapper customerContactMapper;

    @Resource
    private CustomerProjectMapper customerProjectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void addCustomer(CustomerAddRequest request) {


        // 获取当前操作的租户编码
        String tenantCode = TenantPermissionContext.getTenantCode();

        // 2. 防重校验：同一租户下，客户公司名称不能重复
        Long count = customerMapper.selectCount(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getCustomerName, request.getCustomerName())
                .eq(Customer::getTenantCode, tenantCode));
        if (count > 0) {
            throw new BusinessException("该客户已存在，请勿重复添加");
        }

        // 3. 保存客户主表信息
        Customer customer = new Customer();
        // 注意：你的 DTO 叫 customerName，但 Entity 叫 companyName，这里做手动映射
        customer.setCustomerName(request.getCustomerName());
        customer.setCustomerType(request.getCustomerType());
        customer.setConstructionArea(request.getConstructionArea());
        customer.setTenantCode(tenantCode);

        customerMapper.insert(customer);

        Long customerId = customer.getId();

        // 4. 保存客户联系人列表 (1对多)
        if (request.getContacts() != null && !request.getContacts().isEmpty()) {
            for (CustomerContact contactDto : request.getContacts()) {
                CustomerContact contact = new CustomerContact();
                contact.setTenantCode(tenantCode);
                contact.setCustomerId(customerId);
                contact.setContactName(contactDto.getContactName());
                contact.setContactPhone(contactDto.getContactPhone());

                customerContactMapper.insert(contact);
            }
        }

        // 5. 保存客户合作项目列表 (1对多)
        if (request.getProjects() != null && !request.getProjects().isEmpty()) {
            for (CustomerProject projectDto : request.getProjects()) {
                CustomerProject project = new CustomerProject();
                project.setTenantCode(tenantCode);
                project.setCustomerId(customerId);
                project.setProjectName(projectDto.getProjectName());

                customerProjectMapper.insert(project);
            }
        }
    }

    public Page<Customer> pageSearchCustomer(CustomerPageRequest request) {

        String keyword = request.getKeyword();

        // 1. 构建主表查询条件
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Customer::getTenantCode, TenantPermissionContext.getTenantCode());

        // 2. 核心：组装复合搜索条件
        if (StringUtils.isNotBlank(keyword)) {
            // 防止 SQL 注入，将关键字中的单引号转xing义（基础防范）
            String safeKeyword = keyword.replace("'", "''");

            wrapper.and(w -> w
                    // 匹配 ①：客户公司名称
                    .like(Customer::getCustomerName, safeKeyword)
                    // 匹配 ②：项目名称 (利用 inSql 生成 EXISTS/IN 子查询)
                    .or().inSql(Customer::getId,
                            "SELECT customer_id FROM customer_project WHERE project_name LIKE '%" + safeKeyword + "%'")
                    // 匹配 ③：联系人姓名或电话
                    .or().inSql(Customer::getId,
                            "SELECT customer_id FROM customer_contact WHERE (contact_name LIKE '%" + safeKeyword + "%' OR contact_phone LIKE '%" + safeKeyword + "%')")
            );
        }

        // 按创建时间倒序（或者按需调整）
        wrapper.orderByDesc(Customer::getCreateTime);

        // 3. 执行主表的分页查询
        Page<Customer> page = new Page<>(request.getPageNum(), request.getPageSize());
        return customerMapper.selectPage(page, wrapper);
    }

    public CustomerDetailVO getCustomer(Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException("客户不存在");
        }

        List<CustomerContact> customerContactList = customerContactMapper.selectList(new LambdaQueryWrapper<CustomerContact>()
                .eq(CustomerContact::getCustomerId, id));
        List<CustomerProject> customerProjectList = customerProjectMapper.selectList(new LambdaQueryWrapper<CustomerProject>()
                .eq(CustomerProject::getCustomerId, id));

        CustomerDetailVO customerDetailVO = new CustomerDetailVO();
        BeanUtils.copyProperties(customer, customerDetailVO);
        customerDetailVO.setContacts(customerContactList);
        customerDetailVO.setProjects(customerProjectList);
        return customerDetailVO;
    }
}