package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, String> {

    List<Deployment> findByEnvironmentId(String environmentId);

    Optional<Deployment> findByApiIdAndEnvironmentId(String apiId, String environmentId);

    List<Deployment> findByRevisionId(String revisionId);

    boolean existsByRevisionId(String revisionId);

    List<Deployment> findByApiId(String apiId);

    void deleteByRevisionIdAndEnvironmentId(String revisionId, String environmentId);

    boolean existsByApiIdAndEnvironmentId(String apiId, String environmentId);

    List<Deployment> findByOrgId(String orgId);
}
