package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.ServiceRevision;
import com.apisix.controlplane.enums.RevisionState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRevisionRepository extends JpaRepository<ServiceRevision, String> {

    List<ServiceRevision> findByApiId(String apiId);

    Page<ServiceRevision> findByApiId(String apiId, Pageable pageable);

    List<ServiceRevision> findByApiIdOrderByRevisionNumberDesc(String apiId);

    Optional<ServiceRevision> findByApiIdAndRevisionNumber(String apiId, Integer revisionNumber);

    Optional<ServiceRevision> findFirstByApiIdOrderByRevisionNumberDesc(String apiId);

    List<ServiceRevision> findByApiIdAndState(String apiId, RevisionState state);

    List<ServiceRevision> findByApiIdInOrderByApiIdAscRevisionNumberDesc(List<String> apiIds);
}
