package com.unip.fraud.adapter.out.persistence.repository;

import com.unip.fraud.adapter.out.persistence.entity.RejectedRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RejectedRecordRepository extends JpaRepository<RejectedRecordEntity, Long> {
}
