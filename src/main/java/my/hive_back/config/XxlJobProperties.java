package my.hive_back.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    private boolean enabled = false;

    private String adminAddresses = "";

    private String accessToken = "";

    private Executor executor = new Executor();

    @Data
    public static class Executor {

        private String appName = "hive-mini";

        private String address = "";

        private String ip = "";

        private int port = 9998;

        private String logPath = "logs/xxl-job";

        private int logRetentionDays = 30;
    }
}
