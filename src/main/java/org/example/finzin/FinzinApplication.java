package org.example.finzin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinzinApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinzinApplication.class, args);
    }

}
