package my.hive_back.module.order.model.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import my.hive_back.module.order.OrderCategoryEnum;
import org.springframework.util.StringUtils;

public class SalesOrderInformationChannelValidator
        implements ConstraintValidator<ValidSalesOrderInformationChannel, SalesOrderAddRequest> {

    @Override
    public boolean isValid(SalesOrderAddRequest request, ConstraintValidatorContext context) {
        if (request == null || OrderCategoryEnum.DRAWING_BUDGET.getCode().equals(
                OrderCategoryEnum.normalize(request.getOrderCategory()))
                || StringUtils.hasText(request.getInformationChannel())) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("informationChannel")
                .addConstraintViolation();
        return false;
    }
}
