package my.hive_back.module.customer.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerUpdateRequest extends CustomerAddRequest {

    @NotNull(message = "id is required")
    private Long id;
}
