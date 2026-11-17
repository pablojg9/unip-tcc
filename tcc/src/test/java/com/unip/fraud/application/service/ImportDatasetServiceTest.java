package com.unip.fraud.application.service;

import com.unip.fraud.application.domain.ImportFileCommand;
import com.unip.fraud.application.domain.ImportJob;
import com.unip.fraud.application.domain.ImportStatus;
import com.unip.fraud.application.domain.PageResponse;
import com.unip.fraud.application.domain.StoredImportFile;
import com.unip.fraud.application.port.out.importing.ImportFileStorageOutPort;
import com.unip.fraud.application.port.out.importing.ImportJobLauncherOutPort;
import com.unip.fraud.application.port.out.repository.ImportJobRepositoryOutPort;
import com.unip.fraud.application.port.out.repository.ActiveModelRepositoryOutPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportDatasetServiceTest {

  private FakeStorage storage;
  private FakeRepository repository;
  private FakeLauncher launcher;
  private ImportDatasetService service;

  @BeforeEach
  void setUp() {
    storage = new FakeStorage();
    repository = new FakeRepository();
    launcher = new FakeLauncher();
    service = new ImportDatasetService(storage, repository, launcher, () -> true, null, 1024);
  }

  @Test
  void storesPersistsAndLaunchesANewImport() {
    final var result = service.importDataset(command());

    assertThat(result.status()).isEqualTo(ImportStatus.QUEUED.name());
    assertThat(repository.saved).hasSize(1);
    assertThat(launcher.launched).hasSize(1);
    assertThat(storage.discarded).isEmpty();
  }

  @Test
  void reusesThePreviousImportAndDiscardsTheDuplicatedStoredFile() {
    final ImportJob previous = new ImportJob(
        UUID.randomUUID(),
        "claims.csv",
        "/tmp/original.csv",
        "hash",
        ImportStatus.COMPLETED,
        10,
        10,
        0,
        null,
        LocalDateTime.now(),
        LocalDateTime.now(),
        LocalDateTime.now(),
        10L,
        null
    );
    repository.duplicate = previous;

    final var result = service.importDataset(command());

    assertThat(result.importId()).isEqualTo(previous.id());
    assertThat(storage.discarded).containsExactly(storage.stored);
    assertThat(repository.saved).isEmpty();
    assertThat(launcher.launched).isEmpty();
  }

  @Test
  void rejectsAnalysisWhenThereIsNoActiveModel() {
    service = new ImportDatasetService(storage, repository, launcher, () -> false, null, 1024);

    assertThatThrownBy(() -> service.importDataset(command()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Activate a fraud model");
    assertThat(storage.discarded).isEmpty();
    assertThat(repository.saved).isEmpty();
  }

  private ImportFileCommand command() {
    final byte[] content = "id,amount\n1,100\n".getBytes();
    return new ImportFileCommand(
        "claims.csv",
        content.length,
        () -> new ByteArrayInputStream(content)
    );
  }

  private static final class FakeStorage implements ImportFileStorageOutPort {
    private final StoredImportFile stored = new StoredImportFile(
        "claims.csv", "/tmp/duplicate.csv", "hash"
    );
    private final List<StoredImportFile> discarded = new ArrayList<>();

    @Override
    public StoredImportFile store(UUID importId, ImportFileCommand command) {
      return stored;
    }

    @Override
    public void discard(StoredImportFile storedFile) {
      discarded.add(storedFile);
    }
  }

  private static final class FakeLauncher implements ImportJobLauncherOutPort {
    private final List<ImportJob> launched = new ArrayList<>();

    @Override
    public void launch(ImportJob importJob) {
      launched.add(importJob);
    }

    @Override
    public void restart(ImportJob importJob) {
      launched.add(importJob);
    }
  }

  private static final class FakeRepository implements ImportJobRepositoryOutPort {
    private final List<ImportJob> saved = new ArrayList<>();
    private ImportJob duplicate;

    @Override
    public ImportJob save(ImportJob importJob) {
      saved.add(importJob);
      return importJob;
    }

    @Override
    public Optional<ImportJob> findById(UUID importId) {
      return saved.stream().filter(job -> job.id().equals(importId)).findFirst();
    }

    @Override
    public Optional<ImportJob> findLatestByHashAndStatusIn(
        String hash,
        Set<ImportStatus> statuses
    ) {
      return Optional.ofNullable(duplicate);
    }

    @Override
    public PageResponse<ImportJob> findAll(int page, int size) {
      return new PageResponse<>(List.copyOf(saved), page, size, saved.size(), 1);
    }

    @Override
    public void markProcessing(UUID importId, LocalDateTime startedAt) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void markQueued(UUID importId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void markBatchExecutionId(UUID importId, long batchExecutionId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void markFinished(
        UUID importId,
        ImportStatus status,
        long processedRows,
        long rejectedRows,
        String errorMessage,
        LocalDateTime finishedAt
    ) {
      throw new UnsupportedOperationException();
    }
  }
}
