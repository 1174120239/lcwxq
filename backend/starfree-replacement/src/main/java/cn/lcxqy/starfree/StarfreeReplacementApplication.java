package cn.lcxqy.starfree;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StarfreeReplacementApplication {

    public static void main(String[] args) {
        SpringApplication.run(StarfreeReplacementApplication.class, args);
    }
}
