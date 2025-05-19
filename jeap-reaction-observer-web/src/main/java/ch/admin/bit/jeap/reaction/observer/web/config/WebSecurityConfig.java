package ch.admin.bit.jeap.reaction.observer.web.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {

    @Value("${jeap.reaction.observer.read-user.username}")
    private String readUserUsername;

    @Value("${jeap.reaction.observer.read-user.password}")
    private String readUserPassword;

    @Value("${jeap.reaction.observer.write-user.username}")
    private String writeUserUsername;

    @Value("${jeap.reaction.observer.write-user.password}")
    private String writeUserPassword;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 11)
        // same as on the deprecated WebSecurityConfigurerAdapter
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**", "/error")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().hasAnyRole("reaction-observer-write", "reaction-observer-read"));

        http.authenticationManager(createApiAuthManager(http.getSharedObject(AuthenticationManagerBuilder.class)));

        return http.build();
    }

    private AuthenticationManager createApiAuthManager(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
                .withUser(readUserUsername).password(readUserPassword).roles("reaction-observer-read").and()
                .withUser(writeUserUsername).password(writeUserPassword).roles("reaction-observer-write");
        return auth.build();
    }

}
