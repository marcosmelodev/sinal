package br.edu.udf.cs.sinal.plataforma.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// Etapa 4 §20: default-deny — tudo exige autenticação, exceto cadastro e login (refresh entra na Etapa 10b).
// API stateless com Bearer JWT (Resource Server); sem sessão, sem formulário e sem Basic. CSRF fica desligado
// porque nenhuma credencial trafega em cookie; volta com o cookie do refresh token (10b).
// Os cabeçalhos de segurança padrão do Spring Security (nosniff, frame-options, cache) permanecem ativos.
@Configuration(proxyBeanMethods = false)
public class SegurancaConfig {

    @Bean
    SecurityFilterChain cadeiaDaApi(HttpSecurity http, RespostaDeSegurancaProblemJson respostaDeSeguranca)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers(HttpMethod.POST, "/api/v1/usuarios", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
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
}
