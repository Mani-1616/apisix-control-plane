package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.ApiRevision;
import com.apisix.controlplane.enums.RevisionState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiRevisionRepository extends MongoRepository<ApiRevision, String> {
    List<ApiRevision> findByOrgIdAndName(String orgId, String name);
    List<ApiRevision> findByOrgIdAndNameOrderByRevisionNumberDesc(String orgId, String name);
    Optional<ApiRevision> findByOrgIdAndNameAndRevisionNumber(String orgId, String name, Integer revisionNumber);
    boolean existsByOrgIdAndName(String orgId, String name);
    Optional<ApiRevision> findFirstByOrgIdAndNameOrderByRevisionNumberDesc(String orgId, String name);
    List<ApiRevision> findByOrgId(String orgId);
    List<ApiRevision> findByOrgIdAndState(String orgId, RevisionState state);
}

