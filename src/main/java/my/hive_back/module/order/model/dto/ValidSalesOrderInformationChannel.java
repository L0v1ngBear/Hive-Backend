package my.hive_back.module.order.model.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = SalesOrderInformationChannelValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSalesOrderInformationChannel {

    String message() default "Information channel is required";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
