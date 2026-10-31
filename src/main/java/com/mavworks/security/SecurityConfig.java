package com.mavworks.security;

import com.mavworks.auth.LoginView;
import com.mavworks.repository.UserRepository;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users) throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/profile-pictures/**").authenticated());
        http.with(VaadinSecurityConfigurer.vaadin(), config -> config.loginView(LoginView.class).defaultSuccessUrl("/", true));
        http.addFilterAfter(new BannedAccountFilter(users),
                org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users,
            @org.springframework.beans.factory.annotation.Value("${mavworks.admin-user-ids:}") String adminUserIds) {
        var admins = java.util.Arrays.stream(adminUserIds.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).map(Long::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return username -> {
            var user = users.findByUsername(username.trim().toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            var details = org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername()).password(user.getHashedPassword())
                    .disabled(user.isBanned())
                    .roles(admins.contains(user.getId())
                            ? new String[] {"USER", "ADMIN"} : new String[] {"USER"}).build();
            return new AccountPrincipal(user.getId(), details.getUsername(), details.getPassword(),
                    details.isEnabled(), details.getAuthorities());
        };
    }
}
