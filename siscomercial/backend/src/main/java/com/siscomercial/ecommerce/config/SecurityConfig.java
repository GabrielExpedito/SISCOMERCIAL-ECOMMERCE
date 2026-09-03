package com.siscomercial.ecommerce.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers("/api/catalogo/**").permitAll()

                        .requestMatchers("/api/clientes").permitAll()

                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/login/**").permitAll()

                        .requestMatchers("/api/retaguarda/**")
                        .hasRole("ADMIN")

                        .requestMatchers("/api/ia/**")
                        .hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/pedidos/**").hasAnyRole("CLIENTE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/pedidos/**").hasAnyRole("CLIENTE", "ADMIN")

                        .requestMatchers("/api/clientes/**")
                        .authenticated()

                        .requestMatchers("/api/auth/me")
                        .authenticated()

                        .anyRequest()
                        .authenticated()
                )

                .oauth2Login(oauth -> oauth

                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(customOAuth2UserService)
                        )

                        .successHandler((request, response, authentication) -> {

                            System.out.println("======================================");
                            System.out.println("=== LOGIN GOOGLE REALIZADO COM SUCESSO ===");
                            System.out.println("Usuário: " + authentication.getName());
                            System.out.println("======================================");

                            response.sendRedirect("http://localhost:5173");
                        })

                        .failureHandler((request, response, exception) -> {

                            System.out.println("======================================");
                            System.out.println("=== ERRO NO LOGIN GOOGLE ===");
                            System.out.println("======================================");

                            exception.printStackTrace();

                            response.sendRedirect(
                                    "http://localhost:5173/login?erro=google"
                            );
                        })
                )

                .logout(logout -> logout
                        .logoutSuccessUrl("http://localhost:5173")
                        .permitAll()
                );

        return http.build();
    }
}