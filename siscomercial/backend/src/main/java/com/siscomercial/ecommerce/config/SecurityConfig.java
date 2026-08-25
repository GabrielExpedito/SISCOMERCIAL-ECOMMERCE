package com.siscomercial.ecommerce.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

                        // Catalogo publico
                        .requestMatchers("/api/catalogo/**").permitAll()

                        // Cadastro de cliente
                        .requestMatchers("/api/clientes").permitAll()

                        // OAuth2 / Login
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/login/**").permitAll()

                        // Retaguarda
                        .requestMatchers("/api/retaguarda/**")
                        .hasRole("ADMIN")

                        // Assistente IA
                        .requestMatchers("/api/ia/**")
                        .hasRole("ADMIN")

                        // Pedidos
                        .requestMatchers("/api/pedidos/**")
                        .authenticated()

                        // Clientes
                        .requestMatchers("/api/clientes/**")
                        .authenticated()

                        // Demais endpoints
                        .anyRequest()
                        .authenticated()
                )

                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(customOAuth2UserService)
                        )
                );

        return http.build();
    }
}