package com.clinic.koya.koya_clinic_api.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Profile("render")
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        String dbUrl = System.getenv("DB_URL");
        if (dbUrl != null) {
            // Fix the typo in sslmode parameter
            dbUrl = dbUrl.replace("sslmode=requir", "sslmode=require");
        }
        return DataSourceBuilder.create()
                .url(dbUrl)
                .build();
    }
}
