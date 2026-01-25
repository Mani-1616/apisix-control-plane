package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.Environment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentRepository extends MongoRepository<Environment, String> {
    List<Environment> findByOrgId(String orgId);
    Optional<Environment> findByOrgIdAndName(String orgId, String name);
    boolean existsByOrgIdAndName(String orgId, String name);
}

