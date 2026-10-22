package com.finscore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point. Run with: mvn spring-boot:run  ->  http://localhost:8080 */
@SpringBootApplication
public class FinScoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinScoreApplication.class, args);
    }
}
