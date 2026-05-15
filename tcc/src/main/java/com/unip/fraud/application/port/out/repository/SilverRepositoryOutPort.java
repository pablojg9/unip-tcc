package com.unip.fraud.application.port.out.repository;

import com.unip.fraud.application.domain.Transaction;

public interface SilverRepositoryOutPort {
  void save(final String fileName, final Transaction rawPayload);
  long count();
}
