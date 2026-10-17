package br.edu.udf.cs.sinal.identidade.infrastructure.security;

import br.edu.udf.cs.sinal.compartilhado.application.AplicacaoException;
import br.edu.udf.cs.sinal.identidade.domain.model.Usuario;
import br.edu.udf.cs.sinal.identidade.domain.repository.UsuarioRepository;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Set;

// PROVISÓRIO até a autenticação por JWT: confia no cabeçalho X-Usuario-Id, o que permite a qualquer cliente
// se passar por outro usuário. Por isso fica desligado por padrão e só é habilitado no perfil dev.
public class UsuarioAutenticadoProvisorioResolver implements HandlerMethodArgumentResolver {

    public static final String CABECALHO = "X-Usuario-Id";
    public static final String NAO_AUTENTICADO = "NAO_AUTENTICADO";

    private final UsuarioRepository usuarioRepository;
    private final boolean habilitado;

    public UsuarioAutenticadoProvisorioResolver(UsuarioRepository usuarioRepository, boolean habilitado) {
        this.usuarioRepository = usuarioRepository;
        this.habilitado = habilitado;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(UsuarioAutenticado.class)
                && Long.class.equals(parameter.getParameterType());
    }

    // Ausente, inválido, inexistente ou inativo: sempre 401, sem distinguir o motivo ao cliente.
    @Override
    public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        if (!habilitado) {
            throw naoAutenticado();
        }
        Long usuarioId = converter(webRequest.getHeader(CABECALHO));
        return usuarioRepository.buscarPorId(usuarioId)
                .filter(Usuario::getAtivo)
                .map(Usuario::getId)
                .orElseThrow(UsuarioAutenticadoProvisorioResolver::naoAutenticado);
    }

    private static Long converter(String valor) {
        if (valor == null || !valor.matches("[1-9]\\d{0,18}")) {
            throw naoAutenticado();
        }
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException e) {
            throw naoAutenticado();
        }
    }

    private static AplicacaoException naoAutenticado() {
        return new AplicacaoException(NAO_AUTENTICADO, "Autenticação necessária.", Set.of());
    }
}
