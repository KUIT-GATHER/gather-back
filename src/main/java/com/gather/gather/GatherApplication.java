package com.gather.gather;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GatherApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatherApplication.class, args);
	}

}
