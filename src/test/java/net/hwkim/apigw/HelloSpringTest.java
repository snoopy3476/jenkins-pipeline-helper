package net.hwkim.apigw;

import net.hwkim.apigw.ctrl.ApiController;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.boot.test.context.SpringBootTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HelloSpringTest {

  @Autowired
  private WebTestClient webTestClient;
  
  @Test
  public void testHelloSpringPage() {
    this.webTestClient.get().uri("/")
      .exchange()
      .expectStatus().isOk();
  }

}
