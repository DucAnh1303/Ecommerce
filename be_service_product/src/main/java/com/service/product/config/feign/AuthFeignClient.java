package com.service.product.config.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "service-auth", url = "${app.auth-service.url}")
public interface AuthFeignClient {

    @PostMapping("/api/v1/auth/validate")
    UserDto validateToken(@RequestHeader("Authorization") String authorizationHeader);
}
