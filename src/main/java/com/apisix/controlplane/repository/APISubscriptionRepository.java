package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.APISubscription;
import com.apisix.controlplane.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    List<APISubscription> findByOrgIdAndApiId(String orgId, String apiId);

    Page<APISubscription> findByOrgIdAndDeveloperId(String orgId, String developerId, Pageable pageable);

    Page<APISubscription> findByOrgIdAndDeveloperIdAndEnvId(String orgId, String developerId, String envId, Pageable pageable);

    Page<APISubscription> findByOrgIdAndEnvId(String orgId, String envId, Pageable pageable);

    Page<APISubscription> findByOrgId(String orgId, Pageable pageable);

    Page<APISubscription> findByOrgIdAndApiId(String orgId, String apiId, Pageable pageable);

    Optional<APISubscription> findByOrgIdAndDeveloperIdAndApiIdAndEnvId(
            String orgId, String developerId, String apiId, String envId);

    List<APISubscription> findByOrgIdAndDeveloperIdAndEnvIdAndStatus(
            String orgId, String developerId, String envId, SubscriptionStatus status);

    boolean existsByOrgIdAndDeveloperIdAndApiIdAndEnvId(
            String orgId, String developerId, String apiId, String envId);

    Optional<APISubscription> findByOrgIdAndDeveloperIdAndApiIdAndEnvIdAndStatus(
            String orgId, String developerId, String apiId, String envId, SubscriptionStatus status);
}
