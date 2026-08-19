package be_service.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StaticLoadBalancerConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // 1. Auth routes (/api/v1/auth/login, /api/v1/auth/refresh, /api/v1/auth/logout, /api/v1/auth/validate...)
                .route("auth-service", r -> r.path("/api/v1/auth/**").uri("lb://AUTH-SERVICE"))

                // 2. Product routes (/api/v1/product, /api/v1/product/{id}, /api/v1/product/categories...)
                .route("product-service", r -> r.path("/api/v1/product/**").uri("lb://PRODUCT-SERVICE"))

                // 3. Order routes (/api/v1/order/create, /api/v1/order/{id}, /api/v1/order/my-orders...)
                .route("order-service", r -> r.path("/api/v1/order/**").uri("lb://ORDER-SERVICE"))
                .build();
    }
}
