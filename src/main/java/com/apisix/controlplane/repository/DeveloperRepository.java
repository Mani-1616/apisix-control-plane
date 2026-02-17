package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.Developer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeveloperRepository extends JpaRepository<Developer, String> {

    List<Developer> findByOrgId(String orgId);

    Optional<Developer> findByOrgIdAndEmail(String orgId, String email);

    boolean existsByOrgIdAndEmail(String orgId, String email);
}
