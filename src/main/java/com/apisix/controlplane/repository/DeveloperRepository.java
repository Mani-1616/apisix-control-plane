package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.Developer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeveloperRepository extends MongoRepository<Developer, String> {
    
    List<Developer> findByOrgId(String orgId);
    
    Optional<Developer> findByOrgIdAndEmail(String orgId, String email);
    
    boolean existsByOrgIdAndEmail(String orgId, String email);
}

