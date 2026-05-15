package com.unip.fraud.application.port.out.repository;

public interface RejectedRecordRepositoryOutPort {
  void save(final String fileName, final String rawPayload, final String errorReason);
  long count();
}
