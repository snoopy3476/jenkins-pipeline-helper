package net.hwkim.apigw.webclient;

import net.hwkim.apigw.type.*;

import java.util.List;

import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ApiWebClient {
  
  public static Mono<ResponseEntity<String>> sendRequest(Api apival) {
    return WebClient.create(apival.getTargetServer())
      .method(HttpMethod.GET).uri(apival.getTargetPath())
      .retrieve().toEntity(String.class);
  }
}
