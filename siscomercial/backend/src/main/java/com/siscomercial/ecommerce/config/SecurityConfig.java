package com.siscomercial.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                .authorizeHttpRequests(auth -> auth

                        // Catálogo público
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

                        // Pedidos e demais recursos
                        .requestMatchers("/api/pedidos/**")
                        .authenticated()

                        .requestMatchers("/api/clientes/**")
                        .authenticated()

                        // Qualquer outra rota
                        .anyRequest()
                        .authenticated()
                );

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}