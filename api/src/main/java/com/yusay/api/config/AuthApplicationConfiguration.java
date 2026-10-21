package com.yusay.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.random.RandomGenerator;

@Configuration
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class AuthApplicationConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RandomGenerator secureRandomGenerator() {
        return new SecureRandom();
    }
}