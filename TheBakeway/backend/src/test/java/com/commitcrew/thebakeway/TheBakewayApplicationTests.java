package com.commitcrew.thebakeway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Do not reload the initial state: the tests run against the shared database
@SpringBootTest(properties = "bakeway.load-initial-state=false")
class TheBakewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
