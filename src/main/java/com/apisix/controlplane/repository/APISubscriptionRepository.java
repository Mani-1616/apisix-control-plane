package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.APISubscription;
import com.apisix.controlplane.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface APISubscriptionRepository extends JpaRepository<APISubscription, String> {

    List<APISubscription> findByOrgIdAndDeveloperId(String orgId, String developerId);

    List<APISubscription> findByOrgIdAndDeveloperIdAndEnvId(String orgId, String developerId, String envId);

    List<APISubscription> findByOrgIdAndEnvId(String orgId, String envId);

    List<APISubscription> findByOrgId(String orgId);

    Optional<APISubscription> findByOrgIdAndDeveloperIdAndServiceIdAndEnvId(
            String orgId, String developerId, String serviceId, String envId);

    List<APISubscription> findByOrgIdAndDeveloperIdAndEnvIdAndStatus(
            String orgId, String developerId, String envId, SubscriptionStatus status);

    List<APISubscription> findByApisixConsumerId(String apisixConsumerId);

    boolean existsByOrgIdAndDeveloperIdAndServiceIdAndEnvId(
            String orgId, String developerId, String serviceId, String envId);

    Optional<APISubscription> findByOrgIdAndDeveloperIdAndServiceIdAndEnvIdAndStatus(
            String orgId, String developerId, String serviceId, String envId, SubscriptionStatus status);
}
