package com.unip.fraud.application.service;

import com.unip.fraud.application.domain.ImportScoringProgress;
import com.unip.fraud.application.exception.ResourceNotFoundException;
import com.unip.fraud.adapter.out.persistence.repository.ImportJobRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ImportScoringProgressService {
  private final ImportJobRepository imports;
  private final JdbcTemplate jdbc;

  public ImportScoringProgressService(final ImportJobRepository imports, final JdbcTemplate jdbc) {
    this.imports = imports;
    this.jdbc = jdbc;
  }

  public ImportScoringProgress get(final UUID importId) {
    if (!imports.existsById(importId)) {
      throw new ResourceNotFoundException("Import not found");
    }
    return jdbc.queryForObject("""
        SELECT COUNT(g.transaction_id), COUNT(*) - COUNT(g.transaction_id)
        FROM silver.claim s
        LEFT JOIN gold.fraud_prediction g ON g.transaction_id = s.transaction_id
        WHERE s.import_id = ?
        """, (result, row) -> new ImportScoringProgress(
        result.getLong(1), result.getLong(2)
    ), importId);
  }
}
