package br.edu.udf.cs.sinal.identidade.infrastructure.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Injeta o id do usuário autenticado (sub do JWT validado) em parâmetros Long de controllers.
// Resolvido por UsuarioAutenticadoResolver.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UsuarioAutenticado {
}
