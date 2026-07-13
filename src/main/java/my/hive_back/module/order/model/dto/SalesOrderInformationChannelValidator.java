package my.hive_back.module.order.model.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import my.hive_back.module.order.OrderCategoryEnum;
import org.springframework.util.StringUtils;

public class SalesOrderInformationChannelValidator
        implements ConstraintValidator<ValidSalesOrderInformationChannel, Object> {

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String orderCategory;
        String informationChannel;
        if (value instanceof SalesOrderAddRequest request) {
            orderCategory = request.getOrderCategory();
            informationChannel = request.getInformationChannel();
        } else if (value instanceof UnifiedOrderUpdateRequest request) {
            if (request.getOrderCategory() == null) {
                return true;
            }
            orderCategory = request.getOrderCategory();
            informationChannel = request.getInformationChannel();
        } else {
            return true;
        }
        if (OrderCategoryEnum.DRAWING_BUDGET.getCode().equals(OrderCategoryEnum.normalize(orderCategory))
                || StringUtils.hasText(informationChannel)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("informationChannel")
                .addConstraintViolation();
        return false;
    }
}
