package br.edu.udf.cs.sinal.plataforma.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// Etapa 4 §20: default-deny — tudo exige autenticação, exceto cadastro e login (refresh entra na Etapa 10b).
// API stateless com Bearer JWT (Resource Server); sem sessão, sem formulário e sem Basic. CSRF fica desligado
// porque nenhuma credencial trafega em cookie; volta com o cookie do refresh token (10b).
// Os cabeçalhos de segurança padrão do Spring Security (nosniff, frame-options, cache) permanecem ativos.
// CORS por allowlist exata de origens (Etapa 4 §20), nunca "*"; o preflight (OPTIONS) não exige token.
@Configuration(proxyBeanMethods = false)
public class SegurancaConfig {

    @Bean
    SecurityFilterChain cadeiaDaApi(HttpSecurity http, RespostaDeSegurancaProblemJson respostaDeSeguranca)
            throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers(HttpMethod.POST, "/api/v1/usuarios", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(respostaDeSeguranca)
                        .accessDeniedHandler(respostaDeSeguranca))
                .exceptionHandling(excecoes -> excecoes
                        .authenticationEntryPoint(respostaDeSeguranca)
                        .accessDeniedHandler(respostaDeSeguranca));
        return http.build();
    }

    // Origem do front-end local (Vite). Com allowCredentials, as origens precisam ser explícitas.
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(List.of("http://localhost:5173"));
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuracao.setAllowedHeaders(List.of("*"));
        configuracao.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource origem = new UrlBasedCorsConfigurationSource();
        origem.registerCorsConfiguration("/**", configuracao);
        return origem;
    }
}
