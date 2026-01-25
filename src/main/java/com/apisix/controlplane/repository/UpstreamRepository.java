package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.Upstream;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UpstreamRepository extends MongoRepository<Upstream, String> {
    
    /**
     * Find all upstreams for a specific environment
     */
    List<Upstream> findByEnvironmentId(String environmentId);
    
    /**
     * Find upstream by environment and name (for uniqueness check)
     */
    Optional<Upstream> findByEnvironmentIdAndName(String environmentId, String name);
    
    /**
     * Check if upstream exists in environment with given name
     */
    boolean existsByEnvironmentIdAndName(String environmentId, String name);
    
    /**
     * Find all upstreams in an organization (across all environments)
     */
    List<Upstream> findByOrgId(String orgId);
    
    /**
     * Delete all upstreams for an environment
     */
    void deleteByEnvironmentId(String environmentId);
}
