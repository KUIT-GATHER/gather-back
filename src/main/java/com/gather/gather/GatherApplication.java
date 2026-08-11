package com.gather.gather;

import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GatherApplication {

    public static void main(String[] args) {
        validateJvmTimeZone(ZoneId.systemDefault());
        SpringApplication.run(GatherApplication.class, args);
    }

    static void validateJvmTimeZone(ZoneId systemZone) {
        if (!systemZone.normalized().equals(ZoneOffset.UTC)) {
            throw new IllegalStateException(
                    "JVM default time zone must be UTC. "
                            + "Start the application with -Duser.timezone=UTC.");
        }
    }
}
