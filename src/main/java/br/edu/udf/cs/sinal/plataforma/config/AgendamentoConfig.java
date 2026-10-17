package br.edu.udf.cs.sinal.plataforma.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Liga os jobs @Scheduled somente quando habilitados; a suíte de testes os desliga e executa o job diretamente.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "sinal.simulacao.job.habilitado", havingValue = "true")
public class AgendamentoConfig {
}
