package br.edu.udf.cs.sinal;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

// MySQL real para os testes que usam banco (Etapa 4 §25); o datasource é configurado pelo @ServiceConnection.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Um único container por execução, compartilhado por todos os contextos de teste (@SpringBootTest e fatias).
    // destroyMethod vazio: o fechamento de um contexto não derruba o container dos demais; o Ryuk o remove ao final.
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @Bean(destroyMethod = "")
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return MYSQL;
    }
}
