package client.nowhere;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import client.nowhere.config.TestFirestoreConfig;

@SpringBootTest
@Import(TestFirestoreConfig.class)
@ActiveProfiles("test")
class GameApplicationTests {

	@Test
	void contextLoads() {
	}

}
