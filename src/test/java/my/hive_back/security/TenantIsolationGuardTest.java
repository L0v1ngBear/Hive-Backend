package my.hive_back.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantIsolationGuardTest {

    @Test
    void interceptorIgnoredMappersMustDeclareExplicitTenantBoundary() throws IOException {
        Path sourceRoot = Paths.get("src/main/java").toAbsolutePath().normalize();
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("mapper"))
                    .forEach(path -> assertTenantBoundary(path, violations));
        }

        assertTrue(violations.isEmpty(), () ->
                "Mapper files using @InterceptorIgnore(tenantLine = true) must carry explicit tenant_code filtering. "
                        + "Fix the SQL or add a documented platform-only exception. Violations: "
                        + String.join("; ", violations));
    }

    private void assertTenantBoundary(Path path, List<String> violations) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            violations.add(path + " cannot be read: " + e.getMessage());
            return;
        }

        if (!content.contains("@InterceptorIgnore") || !content.contains("tenantLine")) {
            return;
        }
        if (content.contains("TENANT-GUARDED")) {
            return;
        }

        boolean hasExplicitTenantColumn = content.contains("tenant_code");
        boolean hasTenantParameter = content.contains("tenantCode")
                || content.contains("tenant_code AS tenantCode")
                || content.contains("tenant_code,");

        if (!hasExplicitTenantColumn || !hasTenantParameter) {
            violations.add(path.toString());
        }
    }
}
