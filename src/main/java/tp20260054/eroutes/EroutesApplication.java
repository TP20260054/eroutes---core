package tp20260054.eroutes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EroutesApplication {

	public static void main(String[] args) {
		SpringApplication.run(EroutesApplication.class, args);
	}

}
