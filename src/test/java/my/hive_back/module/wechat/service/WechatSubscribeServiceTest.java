package my.hive_back.module.wechat.service;

import my.hive_back.module.wechat.model.vo.WechatSubscribeConfigVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatSubscribeServiceTest {

    @Test
    void configIsEnabledOnlyWhenMiniProgramSubscribeAndTemplateAreReady() {
        WechatSubscribeService service = new WechatSubscribeService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "subscribeEnabled", true);
        ReflectionTestUtils.setField(service, "todoTemplateId", "tmpl_123");

        WechatSubscribeConfigVO config = service.getConfig();

        assertTrue(config.getEnabled());
        assertEquals("tmpl_123", config.getTodoTemplateId());
        assertEquals(1, config.getTemplateIds().size());
    }

    @Test
    void configIsHiddenWhenSubscribeSwitchIsOff() {
        WechatSubscribeService service = new WechatSubscribeService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "subscribeEnabled", false);
        ReflectionTestUtils.setField(service, "todoTemplateId", "tmpl_123");

        WechatSubscribeConfigVO config = service.getConfig();

        assertFalse(config.getEnabled());
        assertTrue(config.getTemplateIds().isEmpty());
    }
}
