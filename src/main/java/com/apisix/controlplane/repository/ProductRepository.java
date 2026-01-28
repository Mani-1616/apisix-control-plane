package com.apisix.controlplane.repository;

import com.apisix.controlplane.entity.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
    
    List<Product> findByOrgId(String orgId);
    
    Optional<Product> findByOrgIdAndName(String orgId, String name);
    
    boolean existsByOrgIdAndName(String orgId, String name);
    
    Optional<Product> findByOrgIdAndId(String orgId, String id);
}

