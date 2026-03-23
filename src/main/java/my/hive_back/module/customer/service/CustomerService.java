package my.hive_back.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import my.hive_back.common.context.TenantPermissionContext;
import my.hive_back.common.exception.BusinessException;
import my.hive_back.module.customer.mapper.CustomerContactMapper;
import my.hive_back.module.customer.mapper.CustomerMapper;
import my.hive_back.module.customer.mapper.CustomerProjectMapper;
import my.hive_back.module.customer.model.dto.CustomerAddRequest;
import my.hive_back.module.customer.model.entity.Customer;
import my.hive_back.module.customer.model.entity.CustomerContact;
import my.hive_back.module.customer.model.entity.CustomerProject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            for (CustomerAddRequest.contacts contactDto : request.getContacts()) {
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
            for (CustomerAddRequest.projects projectDto : request.getProjects()) {
                CustomerProject project = new CustomerProject();
                project.setTenantCode(tenantCode);
                project.setCustomerId(customerId);
                project.setProjectName(projectDto.getProjectName());

                customerProjectMapper.insert(project);
            }
        }
    }
}