package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiServiceRepository extends JpaRepository<Service, String> {

    List<Service> findByOrgId(String orgId);

    Page<Service> findByOrgId(String orgId, Pageable pageable);

    Optional<Service> findByOrgIdAndName(String orgId, String name);

    boolean existsByOrgIdAndName(String orgId, String name);
}
