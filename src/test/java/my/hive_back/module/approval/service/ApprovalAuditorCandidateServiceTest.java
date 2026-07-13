package my.hive_back.module.approval.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import my.hive.common.exception.BusinessException;
import my.hive_back.module.approval.mapper.ApprovalAuditorCandidateMapper;
import my.hive_back.module.approval.model.entity.ApprovalAuditorCandidate;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalAuditorCandidateServiceTest {

    @Mock
    private ApprovalAuditorCandidateMapper mapper;

    private ApprovalAuditorCandidateService service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(ApprovalAuditorCandidateMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ApprovalAuditorCandidate.class);
        service = new ApprovalAuditorCandidateService();
        ReflectionTestUtils.setField(service, "approvalAuditorCandidateMapper", mapper);
    }

    @Test
    void staleDecisionWithZeroAffectedRowsIsRejected() {
        when(mapper.update(any(), any())).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class, () -> service.markAuditorDecision(
                "TENANT-TEST", "ORDER", "sales:SO-1", 2L, true, "approved"));

        assertEquals(409, error.getCode());
    }
}
