package com.stocksense.repository;

import com.stocksense.entity.StoreProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreProfileRepository extends JpaRepository<StoreProfile, Long> {
}
