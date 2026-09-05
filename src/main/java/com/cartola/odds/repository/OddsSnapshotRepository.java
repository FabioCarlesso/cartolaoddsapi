package com.cartola.odds.repository;

import com.cartola.odds.model.OddsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OddsSnapshotRepository extends JpaRepository<OddsSnapshot, Long> {
}
