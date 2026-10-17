package br.edu.udf.cs.sinal.identidade.infrastructure.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Injeta o id do usuário autenticado em parâmetros Long de controllers. A origem do id fica isolada no
// resolver: hoje o cabeçalho provisório; na etapa do JWT, o token, sem alterar os controllers.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UsuarioAutenticado {
}
