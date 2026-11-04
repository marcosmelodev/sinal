package br.edu.udf.cs.sinal;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// PostgreSQL real para os testes que usam banco (Etapa 4 §25); o datasource é configurado pelo @ServiceConnection.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Um único container por execução, compartilhado por todos os contextos de teste (@SpringBootTest e fatias).
    // destroyMethod vazio: o fechamento de um contexto não derruba o container dos demais; o Ryuk o remove ao final.
    // max_connections ampliado: cada contexto de teste em cache mantém o próprio pool (o padrão do PostgreSQL é 100).
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))
            .withCommand("postgres", "-c", "max_connections=500");

    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }
}
