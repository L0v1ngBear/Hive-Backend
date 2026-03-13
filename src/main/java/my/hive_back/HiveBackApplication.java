package my.hive_back;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("my.hive_back.module.**.mapper")
public class HiveBackApplication {

	public static void main(String[] args) {
		SpringApplication.run(HiveBackApplication.class, args);
	}

}
