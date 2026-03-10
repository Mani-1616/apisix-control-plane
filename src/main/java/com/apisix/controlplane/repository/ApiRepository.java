package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.Api;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiRepository extends JpaRepository<Api, String> {

    List<Api> findByOrgId(String orgId);

    Page<Api> findByOrgId(String orgId, Pageable pageable);

    Optional<Api> findByOrgIdAndName(String orgId, String name);

    boolean existsByOrgIdAndName(String orgId, String name);
}
