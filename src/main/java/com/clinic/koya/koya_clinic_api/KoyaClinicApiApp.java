package com.clinic.koya.koya_clinic_api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KoyaClinicApiApp {

    private static final Logger log = LoggerFactory.getLogger(KoyaClinicApiApp.class);

    public static void main(String[] args) {
        // Fix SSL mode typo in DB_URL environment variable
        String dbUrl = System.getenv("DB_URL");
        if (dbUrl != null && dbUrl.contains("sslmode=requir")) {
            System.setProperty("DB_URL", dbUrl.replace("sslmode=requir", "sslmode=require"));
        }
        SpringApplication.run(KoyaClinicApiApp.class, args);
        log.info(":: :: :: === Koya Clinic booking application started === :: :: ::");
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
