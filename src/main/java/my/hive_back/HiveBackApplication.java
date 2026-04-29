package my.hive_back;

import my.hive.common.autoconfigure.HiveCommonAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "my.hive_back")
@MapperScan("my.hive_back.module.**.mapper")
@Import(HiveCommonAutoConfiguration.class)
@EnableAsync
@EnableScheduling
public class HiveBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(HiveBackApplication.class, args);
	}

}
