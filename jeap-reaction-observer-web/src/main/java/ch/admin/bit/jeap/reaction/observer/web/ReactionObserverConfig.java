package ch.admin.bit.jeap.reaction.observer.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;

@AutoConfiguration
@EnableConfigurationProperties
@ComponentScan(basePackageClasses = {ReactionObserverApplication.class})
@PropertySource("classpath:reactionObserverDefaultProperties.properties")
class ReactionObserverConfig {

}
