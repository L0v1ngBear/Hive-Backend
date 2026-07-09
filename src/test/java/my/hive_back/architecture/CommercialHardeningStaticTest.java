package my.hive_back.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import my.hive_back.module.order.service.OrderService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommercialHardeningStaticTest {

    private static final Path MAIN_SOURCE = Path.of("src", "main", "java");
    private static final Pattern SELECT_STAR = Pattern.compile("(?is)SELECT\\s+\\*\\s+FROM");
    private static final Pattern SENSITIVE_OPERATION_BIZ_NO = Pattern.compile(
            "@CollectLog\\([^\\n]*bizNo\\s*=\\s*\"#[^\"]*(phone|mobile|password|token|secret|authorization|openid|sessionkey|username|account|scenekey)[^\"]*\"",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> FORBIDDEN_TEXT = List.of("@Scheduled", "companyAttendanceRule");
    private static final List<String> REMOVED_FEATURE_MARKERS = List.of(
            "A" + "i" + "Advice",
            "a" + "i_advice",
            "A" + "I_ADVICE",
            "dashboard:" + "a" + "i",
            "a" + "iAdvice",
            "advanced" + "A" + "i",
            "\u7ecf\u8425\u5efa\u8bae",
            "\u667a\u80fd\u5efa\u8bae"
    );

    @Test
    void sourceShouldNotReintroduceLegacySchedulerOrCacheKey() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String forbidden : FORBIDDEN_TEXT) {
                if (content.contains(forbidden)) {
                    violations.add(file + " contains " + forbidden);
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    void sourceAndResourcesShouldNotReintroduceRemovedCommercialFeature() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : sourceAndResourceFiles()) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String marker : REMOVED_FEATURE_MARKERS) {
                if (content.contains(marker)) {
                    violations.add(file + " contains removed feature marker: " + marker);
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    void mybatisInlineSqlShouldAvoidSelectStar() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (SELECT_STAR.matcher(content).find()) {
                violations.add(file.toString());
            }
        }
        assertTrue(violations.isEmpty(), "Use explicit columns instead of SELECT *:\n" + String.join(System.lineSeparator(), violations));
    }

    @Test
    void criticalWriteEndpointsShouldHaveOperationAudit() throws IOException {
        Map<String, List<String>> criticalMappings = Map.ofEntries(
                Map.entry("my/hive_back/api/auth/AuthController.java", List.of("/login", "/wechat-login")),
                Map.entry("my/hive_back/api/attendance/AttendanceController.java", List.of("/punch")),
                Map.entry("my/hive_back/api/approval/ApprovalController.java", List.of("/leave/submit", "/leave/audit", "/finance/submit", "/finance/audit", "/resignation/submit", "/resignation/audit")),
                Map.entry("my/hive_back/api/order/SalesOrderController.java", List.of("/orders/{orderId}/status", "/orders/add")),
                Map.entry("my/hive_back/api/order/ProductionOrderController.java", List.of("/orders/{orderId}/status", "/orders/add")),
                Map.entry("my/hive_back/api/wechat/WechatSubscribeController.java", List.of("/register"))
        );
        assertCriticalMappingsAudited(criticalMappings);
    }

    @Test
    void miniProgramPrintTaskEndpointsShouldBeExposedByMiniBackend() throws IOException {
        Path localController = MAIN_SOURCE.resolve("my/hive_back/api/print/PrintTaskController.java");
        assertTrue(Files.notExists(localController), "Print task endpoints must stay in hive-backend-common to avoid duplicate controller beans: " + localController);

        Path commonController = commonPrintTaskController();
        assertTrue(Files.exists(commonController), "Common print task controller must exist: " + commonController.toAbsolutePath().normalize());
        String content = Files.readString(commonController, StandardCharsets.UTF_8);
        List<String> requiredMappings = List.of(
                "@RequestMapping(\"/print-task\")",
                "@GetMapping(\"/pending\")",
                "@GetMapping(\"/recent\")",
                "@GetMapping(\"/pending-count\")",
                "@PostMapping(\"/report\")"
        );
        List<String> violations = requiredMappings.stream()
                .filter(mapping -> !content.contains(mapping))
                .toList();
        assertTrue(violations.isEmpty(), "Mini program print center calls must be backed by mini backend endpoints: " + violations);
    }

    @Test
    void wechatSubscribeRecordsMustUseExplicitTenantBoundary() throws IOException {
        Path service = MAIN_SOURCE.resolve("my/hive_back/module/wechat/service/WechatSubscribeService.java");
        String content = Files.readString(service, StandardCharsets.UTF_8);
        long tenantFilterCount = Pattern.compile("eq\\(WechatSubscribeUser::getTenantCode, tenantCode\\)")
                .matcher(content)
                .results()
                .count();
        assertTrue(tenantFilterCount >= 2,
                "Wechat subscribe authorization reads must explicitly filter tenantCode in register and send paths");
        assertTrue(content.contains("userId == null || !hasText(tenantCode)"),
                "Wechat subscribe registration must reject missing tenant context");
        assertTrue(content.contains("缺少租户上下文"),
                "Wechat subscribe sending must skip when tenant context is missing");
    }

    @Test
    void miniBackendShouldNotExposeLegacyTodoPageApi() throws IOException {
        List<String> violations = new ArrayList<>();
        Path legacyController = MAIN_SOURCE.resolve("my/hive_back/api/todo/TodoController.java");
        if (Files.exists(legacyController)) {
            violations.add("legacy todo controller still exists: " + legacyController);
        }

        Pattern todoRootMapping = Pattern.compile("@RequestMapping\\(\\s*\"/todo\"\\s*\\)");
        for (Path file : javaFiles()) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (todoRootMapping.matcher(content).find()) {
                violations.add(file + " exposes /todo root mapping");
            }
            if (content.contains("\"/todo/list\"")) {
                violations.add(file + " references legacy /todo/list");
            }
        }
        assertTrue(violations.isEmpty(), "Mini program no longer has a todo page; remove legacy todo endpoints:\n"
                + String.join(System.lineSeparator(), violations));
    }

    @Test
    void miniBackendShouldNotRetainLegacyTodoAggregation() throws IOException {
        List<String> violations = new ArrayList<>();
        Path todoModule = MAIN_SOURCE.resolve("my/hive_back/module/todo");
        if (Files.exists(todoModule)) {
            violations.add("legacy todo aggregation module still exists: " + todoModule);
        }

        for (Path file : javaFiles()) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.contains("module.todo") || content.contains("TodoService")) {
                violations.add(file + " references legacy todo aggregation");
            }
            if (content.contains("todoCount") || content.contains("setTodoCount") || content.contains("getTodoCount")) {
                violations.add(file + " exposes retired todoCount summary field");
            }
        }

        assertTrue(violations.isEmpty(), "Mini program no longer consumes todoCount; remove legacy todo aggregation:\n"
                + String.join(System.lineSeparator(), violations));
    }

    @Test
    void miniBackendShouldExposeUnifiedOrderReadEndpoints() throws IOException {
        Path unifiedController = MAIN_SOURCE.resolve("my/hive_back/api/order/OrderController.java");
        assertTrue(Files.exists(unifiedController), "Mini order list should be exposed by unified /orders controller");
        String content = Files.readString(unifiedController, StandardCharsets.UTF_8);
        List<String> requiredMappings = List.of(
                "@RequestMapping(\"/orders\")",
                "@GetMapping(\"/list\")",
                "@GetMapping(\"/status-summary\")",
                "@GetMapping(\"/detail/{orderId}\")",
                "@GetMapping(\"/status-log/{orderId}\")"
        );
        List<String> violations = requiredMappings.stream()
                .filter(mapping -> !content.contains(mapping))
                .toList();
        assertTrue(violations.isEmpty(), "Unified order read controller is missing mappings: " + violations);
    }

    @Test
    void miniApprovalAuditFlagShouldHonorAllSelectedAuditors() throws IOException {
        Path service = MAIN_SOURCE.resolve("my/hive_back/module/approval/service/ApprovalCenterService.java");
        String content = Files.readString(service, StandardCharsets.UTF_8);
        assertTrue(content.contains("parseAuditorIds(auditorIds).contains(currentUserId)"),
                "Mini approval canAudit flags must honor every selected auditor, not only the first auditorId: " + service);
    }

    @Test
    void miniUnifiedOrderListShouldCapPageSize() throws IOException {
        Path service = MAIN_SOURCE.resolve("my/hive_back/module/order/service/OrderService.java");
        String content = Files.readString(service, StandardCharsets.UTF_8);
        assertTrue(content.contains("MAX_PAGE_SIZE"),
                "Mini unified order list must define a maximum page size: " + service);
        assertTrue(content.contains("safePageSize(safeRequest.getPageSize())"),
                "Mini unified order list must normalize requested pageSize before building PageResult: " + service);
        assertTrue(content.contains("Math.min(pageSize, MAX_PAGE_SIZE)"),
                "Mini unified order list must cap pageSize with MAX_PAGE_SIZE: " + service);
        assertTrue(!content.contains("? 20L : safeRequest.getPageSize()"),
                "Mini unified order list must not return caller-provided pageSize directly: " + service);
    }

    @Test
    void orderListPermissionShouldNotBypassStatusPermissions() throws IOException {
        List<Path> services = List.of(
                MAIN_SOURCE.resolve("my/hive_back/module/order/service/SalesOrderService.java"),
                MAIN_SOURCE.resolve("my/hive_back/module/order/service/ProductionOrderService.java")
        );
        List<String> violations = new ArrayList<>();
        for (Path service : services) {
            String content = Files.readString(service, StandardCharsets.UTF_8);
            int methodIndex = content.indexOf("private boolean hasUnrestrictedOrderStatusPermission");
            if (methodIndex < 0) {
                violations.add(service + " missing hasUnrestrictedOrderStatusPermission");
                continue;
            }
            int methodEnd = content.indexOf("    }", methodIndex);
            String methodBody = methodEnd < 0 ? content.substring(methodIndex) : content.substring(methodIndex, methodEnd);
            if (methodBody.contains("PermissionCodeEnum.CODE_ORDER_LIST")) {
                violations.add(service + " treats order:list as unrestricted order status permission");
            }
        }
        assertTrue(violations.isEmpty(), "Order status data must be scoped by status permissions, not only by order:list:\n"
                + String.join(System.lineSeparator(), violations));
    }

    @Test
    void miniUnifiedOrderDetailShouldRecognizeDelimitedSalesOrderIds() throws Exception {
        OrderService orderService = new OrderService();
        Method method = OrderService.class.getDeclaredMethod("isSalesOrder", String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(orderService, "TEST-SO-003"),
                "Unified mini order detail must route imported/test sales order ids to sales detail");
        assertFalse((Boolean) method.invoke(orderService, "TEST-PO-003"),
                "Unified mini order detail must not route production order ids to sales detail");
    }

    @Test
    void operationLogBizNoShouldNotUseSensitiveFields() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (SENSITIVE_OPERATION_BIZ_NO.matcher(content).find()) {
                violations.add(file.toString());
            }
        }
        assertTrue(violations.isEmpty(), "Operation log bizNo is stored separately from sanitized args; do not use sensitive fields:\n"
                + String.join(System.lineSeparator(), violations));
    }

    @Test
    void authOperationLogsShouldNotRecordCredentialPayloads() throws IOException {
        Path file = MAIN_SOURCE.resolve("my/hive_back/api/auth/AuthController.java");
        String content = Files.readString(file, StandardCharsets.UTF_8);
        List<String> sensitiveActions = List.of(
                "action = \"mini_login\"",
                "action = \"wechat_login\"",
                "action = \"join_organization\""
        );
        List<String> violations = sensitiveActions.stream()
                .filter(action -> {
                    int actionIndex = content.indexOf(action);
                    if (actionIndex < 0) {
                        return true;
                    }
                    int annotationStart = content.lastIndexOf("@CollectLog", actionIndex);
                    int annotationEnd = content.indexOf(")", actionIndex);
                    if (annotationStart < 0 || annotationEnd < 0) {
                        return true;
                    }
                    String annotation = content.substring(annotationStart, annotationEnd);
                    return !annotation.contains("recordArgs = false");
                })
                .toList();
        assertTrue(violations.isEmpty(), "Mini auth operation logs must not record credential, phone-code or join-code request payloads: " + violations);
    }

    @Test
    void customerSearchSubqueriesShouldBeScopedByTenant() throws IOException {
        Path file = MAIN_SOURCE.resolve("my/hive_back/module/customer/service/CustomerService.java");
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("FROM customer_project WHERE tenant_code = {1}"),
                "Customer project keyword subqueries must explicitly filter tenantCode: " + file);
        assertTrue(content.contains("FROM customer_contact WHERE tenant_code = {1}"),
                "Customer contact keyword subqueries must explicitly filter tenantCode: " + file);
    }

    @Test
    void customerCrudShouldKeepExplicitTenantBoundary() throws IOException {
        Path file = MAIN_SOURCE.resolve("my/hive_back/module/customer/service/CustomerService.java");
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains(".eq(Customer::getTenantCode, tenantCode)"),
                "Customer parent queries must explicitly filter tenantCode: " + file);
        assertTrue(content.contains(".eq(CustomerContact::getTenantCode, tenantCode)"),
                "Customer contact queries/deletes must explicitly filter tenantCode: " + file);
        assertTrue(content.contains(".eq(CustomerProject::getTenantCode, tenantCode)"),
                "Customer project queries/deletes must explicitly filter tenantCode: " + file);
    }

    @Test
    void documentReadsAndNameUniquenessShouldBeScopedByTenant() throws IOException {
        Path file = MAIN_SOURCE.resolve("my/hive_back/module/document/service/DocumentService.java");
        String content = Files.readString(file, StandardCharsets.UTF_8);

        int listMethodIndex = content.indexOf("public List<Document> selectDocumentByParentId");
        assertTrue(listMethodIndex >= 0, "DocumentService must keep central document list method: " + file);
        String listMethodBody = content.substring(listMethodIndex, Math.min(content.length(), listMethodIndex + 700));
        assertTrue(listMethodBody.contains("queryWrapper.eq(Document::getTenantCode, tenantCode)"),
                "Document list reads must include tenantCode to avoid cross-tenant document leakage: " + file);

        int nameMethodIndex = content.indexOf("private void ensureNameNotExists");
        assertTrue(nameMethodIndex >= 0, "DocumentService must keep central name uniqueness validation: " + file);
        String nameMethodBody = content.substring(nameMethodIndex, Math.min(content.length(), nameMethodIndex + 900));
        assertTrue(nameMethodBody.contains("queryWrapper.eq(Document::getTenantCode, tenantCode)"),
                "Document name uniqueness must include tenantCode to avoid cross-tenant blocking: " + file);
    }

    private static void assertCriticalMappingsAudited(Map<String, List<String>> criticalMappings) throws IOException {
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : criticalMappings.entrySet()) {
            Path file = MAIN_SOURCE.resolve(entry.getKey());
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String mapping : entry.getValue()) {
                int mappingIndex = content.indexOf("@PostMapping(\"" + mapping + "\")");
                if (mappingIndex < 0) {
                    mappingIndex = content.indexOf("@DeleteMapping(\"" + mapping + "\")");
                }
                if (mappingIndex < 0) {
                    violations.add(file + " missing mapping " + mapping);
                    continue;
                }
                int methodIndex = content.indexOf("public ", mappingIndex);
                String annotationBlock = content.substring(mappingIndex, methodIndex < 0 ? Math.min(content.length(), mappingIndex + 500) : methodIndex);
                if (!annotationBlock.contains("@CollectLog(")) {
                    violations.add(file + " mapping " + mapping + " missing @CollectLog");
                }
            }
        }
        assertTrue(violations.isEmpty(), "Critical write endpoints must be audited:\n" + String.join(System.lineSeparator(), violations));
    }

    private static List<Path> javaFiles() throws IOException {
        if (!Files.exists(MAIN_SOURCE)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(MAIN_SOURCE)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static List<Path> sourceAndResourceFiles() throws IOException {
        List<Path> roots = List.of(Path.of("src", "main", "java"), Path.of("src", "main", "resources"));
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                files.addAll(stream
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.toString();
                            return name.endsWith(".java")
                                    || name.endsWith(".yaml")
                                    || name.endsWith(".yml")
                                    || name.endsWith(".sql")
                                    || name.endsWith(".md");
                        })
                        .toList());
            }
        }
        return files;
    }

    private static Path commonPrintTaskController() {
        List<Path> candidates = List.of(
                Path.of("..", "..", "HiveCommon", "hive-backend-common", "src", "main", "java", "my", "hive", "common", "print", "PrintTaskController.java"),
                Path.of("..", "hive-backend-common", "src", "main", "java", "my", "hive", "common", "print", "PrintTaskController.java")
        );
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(candidates.get(0));
    }
}
