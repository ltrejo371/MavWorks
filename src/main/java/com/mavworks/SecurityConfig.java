package com.mavworks;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

@EnableWebSecurity
@Configuration

//Backend for login. Uses in-memory authentication, and needs to be replaced.

public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        http.with(VaadinSecurityConfigurer.vaadin(), configuer ->{
            configuer.loginView(LoginView.class);
        });

        return http.build();
    }

    @Bean 
    public UserDetailsManager userDetailsManager(){
        //DO NOT USE IN PRODUCTION, TODO: Add external authentication
        var user = User.withUsername("user").password("{noop}user").roles("USER").build();
        var mod = User.withUsername("mod").password("{noop}mod").roles("MOD").build();
        var admin = User.withUsername("admin").password("{noop}admin").roles("ADMIN").build();
        return new InMemoryUserDetailsManager(user, mod, admin);
    }
}
