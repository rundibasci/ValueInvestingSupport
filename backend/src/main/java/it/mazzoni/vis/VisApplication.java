package it.mazzoni.vis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class VisApplication {

    public static void main(String[] args) {
        SpringApplication.run(VisApplication.class, args);
    }
}
