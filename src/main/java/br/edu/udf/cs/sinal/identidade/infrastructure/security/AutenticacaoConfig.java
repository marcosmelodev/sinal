package br.edu.udf.cs.sinal.identidade.infrastructure.security;

import br.edu.udf.cs.sinal.identidade.domain.repository.UsuarioRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class AutenticacaoConfig implements WebMvcConfigurer {

    private final UsuarioRepository usuarioRepository;

    public AutenticacaoConfig(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new UsuarioAutenticadoResolver(usuarioRepository));
    }
}
