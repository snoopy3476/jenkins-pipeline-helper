package net.hwkim.apigw.repo;

import net.hwkim.apigw.type.*;

import org.springframework.stereotype.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class ApiRepositoryImpl implements ApiRepository {
    
  Map<String, Api> apiMap = new ConcurrentHashMap<String, Api>(); 
  public ApiRepositoryImpl(){
    apiMap.put("/getreqip", new Api("https://httpbin.org", "/ip"));
    apiMap.put("/getuseragent", new Api("https://httpbin.org", "/user-agent"));
    apiMap.put("/rand.txt", new Api("https://httpbin.org", "/bytes/50"));
  }

  @Override
  public Mono<Api> getApiByReqPath(String str) {
    return Mono.justOrEmpty(apiMap.get(str));
  }
  
}
