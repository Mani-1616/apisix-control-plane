package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.UpstreamBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UpstreamBindingRepository extends JpaRepository<UpstreamBinding, String> {

    /** All upstream bindings for a revision. */
    List<UpstreamBinding> findByRevisionId(String revisionId);

    /** Get the upstream configured for a revision in a specific environment. */
    Optional<UpstreamBinding> findByRevisionIdAndEnvironmentId(String revisionId, String environmentId);

    /** Cleanup all bindings when deleting a revision. */
    void deleteByRevisionId(String revisionId);
}
