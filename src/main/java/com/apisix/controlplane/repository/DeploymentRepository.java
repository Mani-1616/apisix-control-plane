package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, String> {

    /** All deployments in an environment. */
    List<Deployment> findByEnvironmentId(String environmentId);

    /** The one active deployment for a service in an environment (at most one due to UK). */
    Optional<Deployment> findByServiceIdAndEnvironmentId(String serviceId, String environmentId);

    /** All environments where a revision is deployed. */
    List<Deployment> findByRevisionId(String revisionId);

    /** Quick check: is this revision deployed anywhere? */
    boolean existsByRevisionId(String revisionId);

    /** All active deployments for a service. */
    List<Deployment> findByServiceId(String serviceId);

    /** Remove a specific deployment (undeploy). */
    void deleteByRevisionIdAndEnvironmentId(String revisionId, String environmentId);

    /** Quick check: is any revision deployed for this service in this environment? */
    boolean existsByServiceIdAndEnvironmentId(String serviceId, String environmentId);

    /** All deployments in an org. */
    List<Deployment> findByOrgId(String orgId);
}
