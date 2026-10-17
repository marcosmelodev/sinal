package br.edu.udf.cs.sinal.identidade.infrastructure.security;

import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;
import br.edu.udf.cs.sinal.identidade.domain.repository.UsuarioRepository;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Set;

// O usuarioId vem sempre do token validado (sub), nunca do cliente (Etapa 4 §20). O status é conferido no banco
// a cada requisição: um usuário inativado é barrado mesmo com token ainda válido.
public class UsuarioAutenticadoResolver implements HandlerMethodArgumentResolver {

    public static final String NAO_AUTENTICADO = "NAO_AUTENTICADO";
    public static final String CONTA_INATIVA = "CONTA_INATIVA";

    private final UsuarioRepository usuarioRepository;

    public UsuarioAutenticadoResolver(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(UsuarioAutenticado.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken jwt) || jwt.getToken().getSubject() == null
                || !jwt.getToken().getSubject().matches("[1-9]\\d{0,18}")) {
            throw naoAutenticado();
        }
        Usuario usuario = usuarioRepository.buscarPorId(Long.valueOf(jwt.getToken().getSubject()))
                .orElseThrow(UsuarioAutenticadoResolver::naoAutenticado);
        if (!usuario.getAtivo()) {
            throw new AplicacaoException(CONTA_INATIVA, "Conta inativa.", Set.of());
        }
        return usuario.getId();
    }

    private static AplicacaoException naoAutenticado() {
        return new AplicacaoException(NAO_AUTENTICADO, "Autenticação necessária.", Set.of());
    }
}
