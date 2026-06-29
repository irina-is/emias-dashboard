package com.emias.dashboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.base-path:}")
    private String basePath;

    // DefaultRedirectStrategy с contextRelative=true не добавляет context-path к URL.
    // Это нужно потому что basePath уже содержит полный префикс (/spec),
    // и Spring Security не должен его дублировать при наличии server.servlet.context-path.
    private DefaultRedirectStrategy absoluteRedirectStrategy() {
        DefaultRedirectStrategy strategy = new DefaultRedirectStrategy();
        strategy.setContextRelative(false);
        return strategy;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        DefaultRedirectStrategy redirectStrategy = absoluteRedirectStrategy();

        SimpleUrlAuthenticationSuccessHandler successHandler =
                new SimpleUrlAuthenticationSuccessHandler(basePath + "/admin");
        successHandler.setRedirectStrategy(redirectStrategy);

        SimpleUrlAuthenticationFailureHandler failureHandler =
                new SimpleUrlAuthenticationFailureHandler(basePath + "/login?error");
        failureHandler.setRedirectStrategy(redirectStrategy);

        SimpleUrlLogoutSuccessHandler logoutHandler = new SimpleUrlLogoutSuccessHandler();
        logoutHandler.setDefaultTargetUrl(basePath + "/");
        logoutHandler.setRedirectStrategy(redirectStrategy);

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/dashboard", "/hepatitis", "/pcr-dashboard", "/naznachenie", "/scheme-dashboard", "/login").permitAll()
                .requestMatchers("/css/**", "/js/**", "/fonts/**").permitAll()
                .requestMatchers("/api/hcv/weekly-plan", "/api/hcv/weekly-plan/weeks").permitAll()
                .requestMatchers("/api/hcv/mo-work-plan", "/api/hcv/mo-work-plan/months").permitAll()
                .requestMatchers("/api/hcv/kvc").permitAll()
                .requestMatchers("/api/hcv/kpi").permitAll()
                .requestMatchers("/api/hcv/risks").permitAll()
                .requestMatchers("/api/hcv/action-plan").permitAll()
                .requestMatchers("/api/contracts", "/api/contracts/stats").permitAll()
                .requestMatchers("/api/tfoms-ds/dashboard", "/api/tfoms-ds/patients/**", "/api/tfoms-ds/count", "/api/tfoms-ds/summary", "/api/tfoms-ds/dynamics", "/api/tfoms-ds/schemes").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessHandler(logoutHandler)
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .accessDeniedHandler((req, res, e) -> res.sendRedirect(basePath + "/"))
                .authenticationEntryPoint((req, res, e) -> res.sendRedirect(basePath + "/login"))
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.builder()
                .username(adminUsername)
                .password("{noop}" + adminPassword)
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }
}
