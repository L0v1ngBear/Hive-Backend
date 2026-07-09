package my.hive_back.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import my.hive.common.context.TenantPermissionContext;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.customer.mapper.CustomerContactMapper;
import my.hive_back.module.customer.mapper.CustomerMapper;
import my.hive_back.module.customer.mapper.CustomerProjectMapper;
import my.hive_back.module.customer.model.dto.CustomerAddRequest;
import my.hive_back.module.customer.model.dto.CustomerPageRequest;
import my.hive_back.module.customer.model.dto.CustomerUpdateRequest;
import my.hive_back.module.customer.model.entity.Customer;
import my.hive_back.module.customer.model.entity.CustomerContact;
import my.hive_back.module.customer.model.entity.CustomerProject;
import my.hive_back.module.customer.model.vo.CustomerDetailVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 小程序客户服务，复用管理端同一套客户、联系人和合作项目数据。
 */
@Service
public class CustomerService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    @Resource
    private CustomerMapper customerMapper;

    @Resource
    private CustomerContactMapper customerContactMapper;

    @Resource
    private CustomerProjectMapper customerProjectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void addCustomer(CustomerAddRequest request) {
        String tenantCode = TenantPermissionContext.getTenantCode();

        Long count = customerMapper.selectCount(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getTenantCode, tenantCode)
                .eq(Customer::getCustomerName, request.getCustomerName()));
        if (count > 0) {
            throw new BusinessException("该客户已存在，请勿重复添加");
        }

        Customer customer = new Customer();
        customer.setCustomerName(request.getCustomerName());
        customer.setCustomerType(request.getCustomerType());
        customer.setConstructionArea(request.getConstructionArea());
        customer.setTenantCode(tenantCode);
        customerMapper.insert(customer);

        saveContactsAndProjects(tenantCode, customer.getId(), request);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCustomer(CustomerUpdateRequest request) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        Customer customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getTenantCode, tenantCode)
                .eq(Customer::getId, request.getId())
                .last("LIMIT 1"));
        if (customer == null) {
            throw new BusinessException("customer not found");
        }

        Long duplicateCount = customerMapper.selectCount(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getTenantCode, tenantCode)
                .eq(Customer::getCustomerName, request.getCustomerName())
                .ne(Customer::getId, request.getId()));
        if (duplicateCount != null && duplicateCount > 0) {
            throw new BusinessException("customer already exists");
        }

        customer.setCustomerName(request.getCustomerName());
        customer.setCustomerType(request.getCustomerType());
        customer.setConstructionArea(request.getConstructionArea());
        customerMapper.updateById(customer);

        customerContactMapper.delete(new LambdaQueryWrapper<CustomerContact>()
                .eq(CustomerContact::getTenantCode, tenantCode)
                .eq(CustomerContact::getCustomerId, request.getId()));
        customerProjectMapper.delete(new LambdaQueryWrapper<CustomerProject>()
                .eq(CustomerProject::getTenantCode, tenantCode)
                .eq(CustomerProject::getCustomerId, request.getId()));

        saveContactsAndProjects(tenantCode, request.getId(), request);
    }

    public Page<Customer> pageSearchCustomer(CustomerPageRequest request) {
        String keyword = request.getKeyword();
        String tenantCode = TenantPermissionContext.getTenantCode();
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .eq(Customer::getTenantCode, tenantCode);

        if (StringUtils.isNotBlank(keyword)) {
            String safeKeyword = keyword.trim();
            wrapper.and(w -> w
                    .like(Customer::getCustomerName, safeKeyword)
                    .or().apply("id IN (SELECT customer_id FROM customer_project WHERE tenant_code = {1} AND (project_name LIKE CONCAT('%', {0}, '%') OR project_owner LIKE CONCAT('%', {0}, '%')))", safeKeyword, tenantCode)
                    .or().apply("id IN (SELECT customer_id FROM customer_contact WHERE tenant_code = {1} AND (contact_name LIKE CONCAT('%', {0}, '%') OR contact_phone LIKE CONCAT('%', {0}, '%')))", safeKeyword, tenantCode)
            );
        }

        wrapper.orderByDesc(Customer::getCreateTime);
        Page<Customer> page = new Page<>(safePageNum(request.getPageNum()), safePageSize(request.getPageSize()));
        return customerMapper.selectPage(page, wrapper);
    }

    public CustomerDetailVO getCustomer(Long id) {
        String tenantCode = TenantPermissionContext.getTenantCode();
        Customer customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getTenantCode, tenantCode)
                .eq(Customer::getId, id)
                .last("LIMIT 1"));
        if (customer == null) {
            throw new BusinessException("客户不存在");
        }

        List<CustomerContact> customerContactList = customerContactMapper.selectList(new LambdaQueryWrapper<CustomerContact>()
                .eq(CustomerContact::getTenantCode, tenantCode)
                .eq(CustomerContact::getCustomerId, id));
        List<CustomerProject> customerProjectList = customerProjectMapper.selectList(new LambdaQueryWrapper<CustomerProject>()
                .eq(CustomerProject::getTenantCode, tenantCode)
                .eq(CustomerProject::getCustomerId, id));

        CustomerDetailVO customerDetailVO = new CustomerDetailVO();
        BeanUtils.copyProperties(customer, customerDetailVO);
        customerDetailVO.setCompanyName(customer.getCustomerName());
        customerDetailVO.setContacts(customerContactList);
        customerDetailVO.setProjects(customerProjectList);
        return customerDetailVO;
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private void saveContactsAndProjects(String tenantCode, Long customerId, CustomerAddRequest request) {
        if (request.getContacts() != null) {
            for (CustomerContact contactDto : request.getContacts()) {
                if (!StringUtils.isNotBlank(contactDto.getContactName()) && !StringUtils.isNotBlank(contactDto.getContactPhone())) {
                    continue;
                }
                CustomerContact contact = new CustomerContact();
                contact.setTenantCode(tenantCode);
                contact.setCustomerId(customerId);
                contact.setContactName(contactDto.getContactName());
                contact.setContactPhone(contactDto.getContactPhone());
                customerContactMapper.insert(contact);
            }
        }

        if (request.getProjects() != null) {
            for (CustomerProject projectDto : request.getProjects()) {
                if (!StringUtils.isNotBlank(projectDto.getProjectName())) {
                    continue;
                }
                CustomerProject project = new CustomerProject();
                project.setTenantCode(tenantCode);
                project.setCustomerId(customerId);
                project.setProjectName(projectDto.getProjectName().trim());
                project.setProjectOwner(StringUtils.isNotBlank(projectDto.getProjectOwner()) ? projectDto.getProjectOwner().trim() : null);
                customerProjectMapper.insert(project);
            }
        }
    }
}
