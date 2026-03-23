package my.hive_back.api.customer;

import jakarta.annotation.Resource;
import my.hive_back.common.dto.ResultDTO;
import my.hive_back.module.customer.model.dto.CustomerAddRequest;
import my.hive_back.module.customer.service.CustomerService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customer")
@Validated
public class CustomerController {

    @Resource
    private CustomerService customerService;

    @PostMapping("/add")
    public ResultDTO<Void> addCustomer(@RequestBody CustomerAddRequest request) {
        customerService.addCustomer(request);
        return ResultDTO.success(null);
    }
}
