package com.unip.fraud.application.port.out.repository;

public interface BronzeRepositoryOutPort {
  void save(final String fileName, final String rawPayload);
  long count();
}
