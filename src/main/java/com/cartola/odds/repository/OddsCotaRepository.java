package com.cartola.odds.repository;

import com.cartola.odds.model.OddsCota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OddsCotaRepository extends JpaRepository<OddsCota, Long> {
}
