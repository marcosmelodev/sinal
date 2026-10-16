package br.edu.udf.cs.sinal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SinalApplicationTests {

	@Test
	void contextLoads() {
	}

}
