package com.unip.fraud.adapter.out.persistence.repository;

import com.unip.fraud.adapter.out.persistence.entity.BronzeTransactionRawEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BronzeTransactionRawRepository extends JpaRepository<BronzeTransactionRawEntity, Long> {
}
