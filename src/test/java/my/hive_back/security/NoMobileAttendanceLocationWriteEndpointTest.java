package my.hive_back.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NoMobileAttendanceLocationWriteEndpointTest {

    @Test
    void miniProgramMustNotExposeAttendanceLocationWriteEndpoint() throws Exception {
        String controller = Files.readString(
                Paths.get("src/main/java/my/hive_back/api/tenant/TenantController.java"),
                StandardCharsets.UTF_8
        );
        String service = Files.readString(
                Paths.get("src/main/java/my/hive_back/module/tenant/service/TenantService.java"),
                StandardCharsets.UTF_8
        );

        assertFalse(controller.contains("@PostMapping(\"/attendance-location\")"),
                "Company attendance coordinates must be changed only by the management backend.");
        assertFalse(service.contains("saveTenantLocation"),
                "Mini-program backend must not keep the old company-location write service.");
    }
}
