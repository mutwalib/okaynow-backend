package com.okaynow.agencies.repository;

import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionPlanDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionPlanDefinitionRepository extends JpaRepository<SubscriptionPlanDefinition, SubscriptionPlan> {

    List<SubscriptionPlanDefinition> findAllByEnabledTrueOrderBySortOrderAscPlanAsc();
}
