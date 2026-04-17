package com.ncf.demo;

import com.ncf.demo.config.MqttConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DemoApplicationTests {
	@MockBean
	private MqttConfig.MqttGateway mqttGateway;

	@Test
	void contextLoads() {
	}

}
