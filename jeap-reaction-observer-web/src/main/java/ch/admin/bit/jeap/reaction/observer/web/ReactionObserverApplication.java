package ch.admin.bit.jeap.reaction.observer.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class ReactionObserverApplication {

    static void main(String[] args) {
        SpringApplication.run(ReactionObserverApplication.class, args).getEnvironment();
    }
}
