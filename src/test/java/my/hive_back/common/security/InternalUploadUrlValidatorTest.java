package my.hive_back.common.security;

import my.hive.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalUploadUrlValidatorTest {

    @Test
    void financeAttachmentMustComeFromCurrentTenantUploadDirectory() {
        assertEquals(
                "/uploads/finance/TENANT_001/20260515/a.jpg",
                InternalUploadUrlValidator.normalizeOptionalFinanceAttachment(
                        "uploads/finance/TENANT_001/20260515/a.jpg",
                        "TENANT_001"
                )
        );
    }

    @Test
    void rejectsExternalAndCrossTenantFinanceAttachments() {
        assertThrows(BusinessException.class, () -> InternalUploadUrlValidator.normalizeOptionalFinanceAttachment(
                "https://evil.example.com/uploads/finance/TENANT_001/a.jpg",
                "TENANT_001"
        ));
        assertThrows(BusinessException.class, () -> InternalUploadUrlValidator.normalizeOptionalFinanceAttachment(
                "/uploads/finance/TENANT_002/a.jpg",
                "TENANT_001"
        ));
    }

    @Test
    void optionalFinanceAttachmentCanBeBlank() {
        assertNull(InternalUploadUrlValidator.normalizeOptionalFinanceAttachment("", "TENANT_001"));
    }
}
