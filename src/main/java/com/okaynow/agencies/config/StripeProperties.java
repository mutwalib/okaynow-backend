package com.okaynow.agencies.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.stripe")
@Getter
@Setter
public class StripeProperties {

    private String secretKey = "";
    private String webhookSecret = "";
    private String successUrl = "http://localhost:3000/agency/billing?checkout=success";
    private String cancelUrl = "http://localhost:3000/agency/billing?checkout=cancel";
    private String priceStarter = "";
    private String priceProfessional = "";
    private String priceFeatured = "";
    private String connectReturnUrl = "http://localhost:3000/agency/billing?connect=return";
    private String connectRefreshUrl = "http://localhost:3000/agency/billing?connect=refresh";
    private String invoiceSuccessUrl = "http://localhost:3000/client/billing?paid=success";
    private String invoiceCancelUrl = "http://localhost:3000/client/billing?paid=cancel";

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }
}
