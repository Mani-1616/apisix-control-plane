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

    List<ServiceRevision> findByServiceId(String serviceId);

    Page<ServiceRevision> findByServiceId(String serviceId, Pageable pageable);

    List<ServiceRevision> findByServiceIdOrderByRevisionNumberDesc(String serviceId);

    Optional<ServiceRevision> findByServiceIdAndRevisionNumber(String serviceId, Integer revisionNumber);

    Optional<ServiceRevision> findFirstByServiceIdOrderByRevisionNumberDesc(String serviceId);

    List<ServiceRevision> findByServiceIdAndState(String serviceId, RevisionState state);

    List<ServiceRevision> findByServiceIdInOrderByServiceIdAscRevisionNumberDesc(List<String> serviceIds);
}
