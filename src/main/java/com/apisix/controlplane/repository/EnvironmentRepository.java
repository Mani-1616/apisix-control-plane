package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, String> {
    List<Environment> findByOrgId(String orgId);
    Optional<Environment> findByOrgIdAndName(String orgId, String name);
    boolean existsByOrgIdAndName(String orgId, String name);
}
