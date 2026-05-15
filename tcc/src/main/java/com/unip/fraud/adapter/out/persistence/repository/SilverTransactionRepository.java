package com.unip.fraud.adapter.out.persistence.repository;

import com.unip.fraud.adapter.out.persistence.entity.SilverTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SilverTransactionRepository extends JpaRepository<SilverTransactionEntity, String> {
}
