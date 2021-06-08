package net.hwkim.apigw.ctrl;

import net.hwkim.apigw.type.*;
import net.hwkim.apigw.repo.*;
import net.hwkim.apigw.webclient.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;

@RestController
public class ApiController {
  @Autowired
  private ApiRepository apiRepository;

  @GetMapping("/apigw/{*reqPath}")
  Mono<ResponseEntity<String>> findByReqPath(@PathVariable String reqPath) {

    Mono<Api> api = this.apiRepository.getApiByReqPath(reqPath);

    Mono<ResponseEntity<String>> remoteRes = api
      
      // send request, and get response
      .flatMap(apival -> ApiWebClient.sendRequest(apival))
      
      // return not found if mono is empty
      .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));


                       /*new HttpHeaders())
                     .doOnNext(header -> header.add("Location", "/404.htm"))
                     .map(header -> new ResponseEntity<>(null, header, HttpStatus.MOVED_PERMANENTLY)));
        /*Mono.just(ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body("<title>404 NOT FOUND</title><style>html{height: 100%;}body{background: url(/images/404-not-found.jpg) no-repeat center center fixed;background-size: cover;}</style><h1>HWKIM API GATEWAY - 404 NOT FOUND</h1><p></p><p>Try:</p><ul><li><a href='/apigw/getreqip'>/apigw/getreqip</a></li><li><a href='/apigw/getuseragent'>/apigw/getuseragent</a></li><li><a href='/apigw/rand.txt'>/apigw/rand.txt</a></li></ul>")) );
        */
    return remoteRes;

  }

  /*
  @RequestMapping("/")
  public String index() {
    return "index";
  }
  */  
/*
  @GetMapping("/")
  public Mono<ResponseEntity<String>> helloSpring() {
    return Mono.just(new HttpHeaders())
      .doOnNext(header -> header.add("Location", "/hello.htm"))
      .map(header -> new ResponseEntity<>(null, header, HttpStatus.MOVED_PERMANENTLY));
  }
*/
}
