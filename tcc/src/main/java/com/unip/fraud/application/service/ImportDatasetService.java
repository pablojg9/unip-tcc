package com.unip.fraud.application.service;

import com.unip.fraud.application.domain.ImportFileCommand;
import com.unip.fraud.application.domain.ImportJob;
import com.unip.fraud.application.domain.ImportJobView;
import com.unip.fraud.application.domain.ImportStatus;
import com.unip.fraud.application.domain.PageResponse;
import com.unip.fraud.application.domain.StoredImportFile;
import com.unip.fraud.application.exception.ResourceNotFoundException;
import com.unip.fraud.application.port.in.GetImportsUseCase;
import com.unip.fraud.application.port.in.ImportDatasetUseCase;
import com.unip.fraud.application.port.out.importing.ImportFileStorageOutPort;
import com.unip.fraud.application.port.out.importing.ImportJobLauncherOutPort;
import com.unip.fraud.application.port.out.repository.ImportJobRepositoryOutPort;
import com.unip.fraud.application.port.out.repository.ActiveModelRepositoryOutPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static com.unip.fraud.application.validation.Validation.requireArgument;
import static java.util.Objects.nonNull;

@Service
public class ImportDatasetService implements ImportDatasetUseCase, GetImportsUseCase {

  private static final Set<ImportStatus> DEDUPLICATED_STATUSES = Set.of(
      ImportStatus.QUEUED,
      ImportStatus.PROCESSING,
      ImportStatus.COMPLETED,
      ImportStatus.COMPLETED_WITH_WARNINGS
  );

  private final ImportFileStorageOutPort storage;
  private final ImportJobRepositoryOutPort repository;
  private final ImportJobLauncherOutPort launcher;
  private final ActiveModelRepositoryOutPort activeModels;
  private final ScoringCancellationService cancellations;
  private final long maxFileSize;

  public ImportDatasetService(
      final ImportFileStorageOutPort storage,
      final ImportJobRepositoryOutPort repository,
      final ImportJobLauncherOutPort launcher,
      final ActiveModelRepositoryOutPort activeModels,
      final ScoringCancellationService cancellations,
      @Value("${imports.max-file-size}") final long maxFileSize) {
    this.storage = storage;
    this.repository = repository;
    this.launcher = launcher;
    this.activeModels = activeModels;
    this.cancellations = cancellations;
    this.maxFileSize = maxFileSize;
  }

  @Override
  public ImportJobView importDataset(final ImportFileCommand command) {
    validate(command);
    requireArgument(
        activeModels.hasActiveModel(),
        "Activate a fraud model before submitting transactions for analysis"
    );
    final UUID importId = UUID.randomUUID();
    final StoredImportFile storedFile = storage.store(importId, command);
    final var duplicate = repository.findLatestByHashAndStatusIn(
        storedFile.sha256(),
        DEDUPLICATED_STATUSES
    );
    return duplicate
        .filter(importJob -> importJob.scoringCancelledAt() == null)
        .map(importJob -> duplicatedImport(storedFile, importJob))
        .orElseGet(() -> startImport(importId, storedFile));
  }

  private ImportJobView duplicatedImport(
      final StoredImportFile storedFile,
      final ImportJob importJob) {
    storage.discard(storedFile);
    return importJob.toView();
  }

  private ImportJobView startImport(
      final UUID importId,
      final StoredImportFile storedFile) {
    try {
      final ImportJob importJob = repository.save(ImportJob.queued(importId, storedFile));
      launcher.launch(importJob);
      return importJob.toView();
    } catch (RuntimeException exception) {
      storage.discard(storedFile);
      throw exception;
    }
  }

  @Override
  public ImportJobView retry(final UUID importId) {
    final ImportJob importJob = repository.findById(importId)
        .orElseThrow(() -> new ResourceNotFoundException("Import not found"));
    requireArgument(
        importJob.status() == ImportStatus.FAILED,
        "Only failed imports can be restarted"
    );
    requireArgument(
        importJob.batchExecutionId() != null,
        "Import has no restartable Batch execution"
    );
    repository.markQueued(importId);
    launcher.restart(importJob);
    return repository.findById(importId).orElse(importJob).toView();
  }

  @Override
  public ImportJobView cancelScoring(final UUID importId) {
    return cancellations.cancel(importId);
  }

  @Override
  public ImportJobView getImport(final UUID importId) {
    return repository.findById(importId)
        .map(ImportJob::toView)
        .orElseThrow(() -> new ResourceNotFoundException("Import not found"));
  }

  @Override
  public PageResponse<ImportJobView> getImports(final int page, final int size) {
    final PageResponse<ImportJob> result = repository.findAll(
        Math.max(page, 0),
        Math.min(Math.max(size, 1), 100)
    );
    return new PageResponse<>(
        result.content().stream().map(ImportJob::toView).toList(),
        result.page(),
        result.size(),
        result.totalElements(),
        result.totalPages()
    );
  }

  private void validate(final ImportFileCommand command) {
    requireArgument(command != null, "The dataset command is required");
    requireArgument(nonNull(command.fileName()) && !command.fileName().isBlank(), "The dataset must have a file name");
    requireArgument(command.size() > 0, "Select a non-empty dataset file");
    requireArgument(command.size() <= maxFileSize, "The dataset exceeds the configured size limit");
    requireArgument(Set.of(".csv", ".xlsx", ".xls").stream().anyMatch(command.fileName().toLowerCase(Locale.ROOT)::endsWith), "Supported formats: .csv, .xlsx and .xls"
    );
  }
}
