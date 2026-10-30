package com.siscomercial.ecommerce.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final CustomOAuth2UserService customOAuth2UserService;

    @Value("${siscomercial.frontend-url}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                )

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers("/api/catalogo/**").permitAll()
                        .requestMatchers("/uploads/produtos/**").permitAll()

                        .requestMatchers("/api/clientes").permitAll()

                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/login/**").permitAll()
                        .requestMatchers("/api/auth/csrf").permitAll()

                        .requestMatchers("/api/retaguarda/marketplaces/mercado-livre/callback")
                        .permitAll()
                        .requestMatchers("/api/retaguarda/marketplaces/shopee/callback")
                        .permitAll()
                        .requestMatchers("/api/retaguarda/marketplaces/magalu/callback")
                        .permitAll()

                        .requestMatchers("/api/retaguarda/**")
                        .hasRole("ADMIN")

                        .requestMatchers("/api/ia/**")
                        .hasRole("ADMIN")

                        // Nao existe provedor de pagamento confiavel integrado; negar a rota simulada.
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/confirmar-pagamento").denyAll()
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/status").hasRole("ADMIN")

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
                            response.sendRedirect(frontendUrl);
                        })

                        .failureHandler((request, response, exception) -> {
                            log.error("Falha na autenticacao OAuth do Google: tipo={}, mensagem={}",
                                    exception.getClass().getSimpleName(), exception.getMessage());
                            String urlFrontend = frontendUrl.replaceAll("/+$", "");
                            response.sendRedirect(urlFrontend + "/?erro=google");
                        })
                )

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
