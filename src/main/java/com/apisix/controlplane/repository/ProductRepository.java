package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    List<Product> findByOrgId(String orgId);

    List<Product> findByOrgIdAndEnvId(String orgId, String envId);

    boolean existsByOrgIdAndEnvIdAndName(String orgId, String envId, String name);

    Optional<Product> findByOrgIdAndId(String orgId, String id);
}
