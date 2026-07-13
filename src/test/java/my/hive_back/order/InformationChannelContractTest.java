package my.hive_back.order;

import com.baomidou.mybatisplus.annotation.TableField;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;
import my.hive_back.module.installation.model.entity.InstallationTask;
import my.hive_back.module.order.OrderCategoryEnum;
import my.hive_back.module.order.model.dto.ProductionOrderAddRequest;
import my.hive_back.module.order.model.dto.SalesOrderAddRequest;
import my.hive_back.module.order.model.entity.ProductionOrder;
import my.hive_back.module.order.model.entity.SalesOrder;
import my.hive_back.module.order.model.vo.ProductionOrderVO;
import my.hive_back.module.order.model.vo.SalesOrderVO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class InformationChannelContractTest {

    private static final Path MAIN_SOURCE = Path.of("src", "main", "java");

    @Test
    void publicOrderTypesExposeOnlyStringInformationChannel() {
        List<Class<?>> contractTypes = List.of(
                SalesOrder.class,
                ProductionOrder.class,
                InstallationTask.class,
                SalesOrderAddRequest.class,
                ProductionOrderAddRequest.class,
                SalesOrderVO.class,
                ProductionOrderVO.class
        );

        List<String> violations = new ArrayList<>();
        for (Class<?> contractType : contractTypes) {
            Field informationChannel = findField(contractType, "informationChannel");
            if (informationChannel == null) {
                violations.add(contractType.getSimpleName() + " is missing informationChannel");
            } else if (!String.class.equals(informationChannel.getType())) {
                violations.add(contractType.getSimpleName() + ".informationChannel must be String");
            }
            if (findField(contractType, "deliveryDate") != null) {
                violations.add(contractType.getSimpleName() + " still exposes deliveryDate");
            }
        }

        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    void persistencePrintAndInstallationSyncUseOnlyInformationChannel() throws IOException {
        assertEntityColumn(SalesOrder.class);
        assertEntityColumn(ProductionOrder.class);
        assertEntityColumn(InstallationTask.class);

        assertContains("module/order/mapper/SalesOrderMapper.java", "information_channel");
        assertContains("module/order/mapper/ProductionOrderMapper.java", "information_channel");
        assertContains("module/installation/mapper/InstallationTaskMapper.java", "information_channel");
        assertContains("module/order/service/OrderFlowPrintService.java", "payload.put(\"informationChannel\"");
        assertContains("module/installation/service/InstallationTaskSyncService.java",
                "task.setInformationChannel(order.getInformationChannel())");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(MAIN_SOURCE)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path, StandardCharsets.UTF_8);
                            if (source.contains("deliveryDate") || source.contains("delivery_date")) {
                                violations.add(path.toString());
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException("Unable to read " + path, exception);
                        }
                    });
        }
        assertTrue(violations.isEmpty(), "Legacy delivery-date contract remains in:\n" + String.join("\n", violations));
    }

    @Test
    void informationChannelValidationMatchesOrderCategoryRules() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();

            SalesOrderAddRequest regularOrder = salesOrderRequest(OrderCategoryEnum.BULK.getCode());
            Set<ConstraintViolation<SalesOrderAddRequest>> regularOrderViolations = validator.validate(regularOrder);
            assertTrue(hasViolation(regularOrderViolations, "informationChannel"),
                    "Ordinary sales orders must require informationChannel: " + regularOrderViolations);

            SalesOrderAddRequest drawingBudgetOrder = salesOrderRequest(OrderCategoryEnum.DRAWING_BUDGET.getCode());
            assertFalse(hasViolation(validator.validate(drawingBudgetOrder), "informationChannel"),
                    "drawing_budget sales orders may omit informationChannel");

            ProductionOrderAddRequest productionOrder = productionOrderRequest();
            assertTrue(hasViolation(validator.validate(productionOrder), "informationChannel"),
                    "Production orders must require informationChannel");
        }
    }

    private void assertEntityColumn(Class<?> entityType) {
        Field field = findField(entityType, "informationChannel");
        assertTrue(field != null, entityType.getSimpleName() + " is missing informationChannel");
        assertEquals(String.class, field.getType(), entityType.getSimpleName() + ".informationChannel must be String");
        TableField tableField = field.getAnnotation(TableField.class);
        assertTrue(tableField != null, entityType.getSimpleName() + ".informationChannel must declare its database column");
        assertEquals("information_channel", tableField.value(),
                entityType.getSimpleName() + ".informationChannel must use information_channel");
    }

    private void assertContains(String relativePath, String expected) throws IOException {
        String source = Files.readString(MAIN_SOURCE.resolve("my/hive_back").resolve(relativePath), StandardCharsets.UTF_8);
        assertTrue(source.contains(expected), relativePath + " must contain " + expected);
    }

    private SalesOrderAddRequest salesOrderRequest(String orderCategory) {
        SalesOrderAddRequest request = new SalesOrderAddRequest();
        request.setCustomerName("Customer");
        request.setProjectName("Project");
        request.setOrderCategory(orderCategory);
        return request;
    }

    private ProductionOrderAddRequest productionOrderRequest() {
        ProductionOrderAddRequest request = new ProductionOrderAddRequest();
        request.setModelCode("MODEL");
        request.setWeight(1F);
        request.setSpec(1F);
        request.setQuantity(1);
        return request;
    }

    private boolean hasViolation(Set<? extends ConstraintViolation<?>> violations, String propertyPath) {
        return violations.stream()
                .anyMatch(violation -> propertyPath.equals(violation.getPropertyPath().toString()));
    }

    private Field findField(Class<?> type, String fieldName) {
        try {
            return type.getDeclaredField(fieldName);
        } catch (NoSuchFieldException exception) {
            return null;
        }
    }
}
