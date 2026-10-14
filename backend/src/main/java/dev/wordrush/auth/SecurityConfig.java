package dev.wordrush.auth;

import dev.wordrush.config.WordRushProperties;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless JWT security. Every request carries {@code Authorization: Bearer <token>};
 * Spring's resource-server filter verifies it and we turn the claims into an
 * {@link AuthenticatedUser} principal. No sessions and no cookies, so CSRF protection
 * (which defends cookie-authenticated browsers) is not needed.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
        // Filters run before Spring MVC, so @RestControllerAdvice can't see their exceptions.
        // Hand them to MVC's resolver so 401/403 come out as the same problem+json as everything else.
        AuthenticationEntryPoint unauthenticated = (request, response, ex) ->
                resolver.resolveException(request, response, null, ex);
        AccessDeniedHandler forbidden = (request, response, ex) ->
                resolver.resolveException(request, response, null, ex);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/guest").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").denyAll() // metrics stay private (Stage 8)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/ws", "/ws/**").permitAll() // JWT checked on STOMP CONNECT
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(toAuthenticatedUser()))
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(forbidden));
        return http.build();
    }

    private static Converter<Jwt, AbstractAuthenticationToken> toAuthenticatedUser() {
        return jwt -> {
            AuthenticatedUser user = AuthenticatedUser.from(jwt);
            var authority = new SimpleGrantedAuthority(user.bot() ? "ROLE_BOT" : "ROLE_PLAYER");
            return UsernamePasswordAuthenticationToken.authenticated(user, jwt, List.of(authority));
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(WordRushProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
