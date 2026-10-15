package br.edu.udf.cs.sinal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// ddl-auto=create-drop é temporário: mantém o schema gerado pelo Hibernate até a adoção do Flyway (Etapa 7.1b).
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(TestcontainersConfiguration.class)
class SinalApplicationTests {

	@Test
	void contextLoads() {
	}

}
