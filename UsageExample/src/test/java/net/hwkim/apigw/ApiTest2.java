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
public class ApiTest2 {

  @Autowired
  private WebTestClient webTestClient;

  @Test
  public void testGetReqIp() {
    this.webTestClient.get().uri("/apigw/getreqip")
      .exchange()
      .expectStatus().isOk();
  }


  @Test
  public void testGetUserAgent() {
    this.webTestClient.get().uri("/apigw/getuseragent")
      .exchange()
      .expectStatus().isOk();
  }

  @Test
  public void testRandTxt() {
    this.webTestClient.get().uri("/apigw/rand.txt")
      .exchange()
      .expectStatus().isOk();
  }

}
