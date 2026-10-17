package br.edu.udf.cs.sinal.identidade.infrastructure.security;

import br.edu.udf.cs.sinal.identidade.domain.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class AutenticacaoProvisoriaConfig implements WebMvcConfigurer {

    private final UsuarioRepository usuarioRepository;
    private final boolean cabecalhoProvisorioHabilitado;

    public AutenticacaoProvisoriaConfig(UsuarioRepository usuarioRepository,
                                        @Value("${sinal.autenticacao.cabecalho-provisorio.habilitado:false}")
                                        boolean cabecalhoProvisorioHabilitado) {
        this.usuarioRepository = usuarioRepository;
        this.cabecalhoProvisorioHabilitado = cabecalhoProvisorioHabilitado;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new UsuarioAutenticadoProvisorioResolver(usuarioRepository, cabecalhoProvisorioHabilitado));
    }
}
