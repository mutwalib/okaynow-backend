package com.okaynow.agencies.config;

import com.okaynow.agencies.service.SubscriptionPlanCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@RequiredArgsConstructor
public class SubscriptionPlanBootstrapConfig implements ApplicationRunner {

    private final SubscriptionPlanCatalogService catalogService;

    @Override
    public void run(ApplicationArguments args) {
        catalogService.ensureDefaultsSeeded();
    }
}
