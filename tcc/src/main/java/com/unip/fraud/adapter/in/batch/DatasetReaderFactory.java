package com.unip.fraud.adapter.in.batch;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Component
public class DatasetReaderFactory {

  private final List<DatasetReaderStrategy> strategies;

  public DatasetReaderFactory(final List<DatasetReaderStrategy> strategies) {
    this.strategies = List.copyOf(strategies);
  }

  public DatasetRowCursor open(
      final Path file,
      final UUID importId,
      final String originalFileName) {
    return strategies.stream()
        .filter(strategy -> strategy.supports(originalFileName))
        .findFirst()
        .map(strategy -> open(strategy, file, importId, originalFileName))
        .orElseThrow(() -> new IllegalArgumentException("Supported formats: .csv, .xlsx and .xls"));
  }

  private DatasetRowCursor open(
      final DatasetReaderStrategy strategy,
      final Path file,
      final UUID importId,
      final String originalFileName) {
    try {
      return strategy.open(file, importId, originalFileName);
    } catch (Exception exception) {
      throw new IllegalStateException("Could not open dataset " + originalFileName, exception);
    }
  }
}
