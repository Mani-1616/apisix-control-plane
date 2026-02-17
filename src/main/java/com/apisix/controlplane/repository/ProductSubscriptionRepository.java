package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.ProductSubscription;
import com.apisix.controlplane.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSubscriptionRepository extends JpaRepository<ProductSubscription, String> {

    List<ProductSubscription> findByOrgId(String orgId);

    List<ProductSubscription> findByOrgIdAndDeveloperId(String orgId, String developerId);

    List<ProductSubscription> findByOrgIdAndEnvId(String orgId, String envId);

    List<ProductSubscription> findByOrgIdAndDeveloperIdAndEnvId(String orgId, String developerId, String envId);

    Optional<ProductSubscription> findByOrgIdAndDeveloperIdAndProductIdAndEnvId(
            String orgId, String developerId, String productId, String envId);

    List<ProductSubscription> findByOrgIdAndDeveloperIdAndEnvIdAndStatus(
            String orgId, String developerId, String envId, SubscriptionStatus status);

    List<ProductSubscription> findByConsumerGroupId(String consumerGroupId);

    List<ProductSubscription> findByProductId(String productId);

    List<ProductSubscription> findByOrgIdAndProductId(String orgId, String productId);
}
