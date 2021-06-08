package net.hwkim.apigw.repo;

import net.hwkim.apigw.type.*;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ApiRepository {
  Mono<Api> getApiByReqPath(String str);
}
