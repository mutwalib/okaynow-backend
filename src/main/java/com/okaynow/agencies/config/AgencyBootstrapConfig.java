package com.okaynow.agencies.config;

import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionStatus;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.agencies.support.AgencySlugService;
import com.okaynow.users.domain.Qualification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Ensures a legacy default tenant exists for single-agency data during migration.
 */
@Component
@Order(40)
@RequiredArgsConstructor
@Slf4j
public class AgencyBootstrapConfig implements ApplicationRunner {

    private final AgencyRepository agencyRepository;
    private final AgencySlugService agencySlugService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (agencyRepository.count() > 0) {
            return;
        }
        Agency agency = Agency.builder()
                .slug(agencySlugService.generateUniqueSlug("OkayNow Care"))
                .legalName("OkayNow Care")
                .displayName("OkayNow Care")
                .city("Boston")
                .state("MA")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionPlan(SubscriptionPlan.PROFESSIONAL)
                .subscriptionPeriodStart(Instant.now())
                .subscriptionPeriodEnd(Instant.now().plus(365, ChronoUnit.DAYS))
                .directoryListed(true)
                .publicDescription(
                        "Massachusetts home care agency powered by OkayNow. Connect with us for PCA, HHA, and skilled nursing support.")
                .qualificationsSupported(Set.of(
                        Qualification.PCA,
                        Qualification.HHA,
                        Qualification.CNA,
                        Qualification.LPN,
                        Qualification.RN))
                .build();
        agencyRepository.save(agency);
        log.info("Bootstrapped default agency tenant {}", agency.getSlug());
    }
}
