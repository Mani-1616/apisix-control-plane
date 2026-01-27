package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.APISubscription;
import com.apisix.controlplane.enums.SubscriptionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface APISubscriptionRepository extends MongoRepository<APISubscription, String> {
    
    List<APISubscription> findByOrgIdAndDeveloperId(String orgId, String developerId);
    
    List<APISubscription> findByOrgIdAndDeveloperIdAndEnvId(String orgId, String developerId, String envId);
    
    List<APISubscription> findByOrgIdAndEnvId(String orgId, String envId);
    
    List<APISubscription> findByOrgId(String orgId);
    
    Optional<APISubscription> findByOrgIdAndDeveloperIdAndApiNameAndEnvId(
            String orgId, String developerId, String apiName, String envId);
    
    List<APISubscription> findByOrgIdAndDeveloperIdAndEnvIdAndStatus(
            String orgId, String developerId, String envId, SubscriptionStatus status);
    
    List<APISubscription> findByApisixConsumerId(String apisixConsumerId);
    
    boolean existsByOrgIdAndDeveloperIdAndApiNameAndEnvId(
            String orgId, String developerId, String apiName, String envId);
    
    Optional<APISubscription> findByOrgIdAndDeveloperIdAndApiNameAndEnvIdAndStatus(
            String orgId, String developerId, String apiName, String envId, SubscriptionStatus status);
}

