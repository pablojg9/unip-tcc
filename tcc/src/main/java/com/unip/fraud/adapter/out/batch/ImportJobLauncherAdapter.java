package com.unip.fraud.adapter.out.batch;

import com.unip.fraud.application.domain.ImportJob;
import com.unip.fraud.application.domain.ImportStatus;
import com.unip.fraud.application.port.out.importing.ImportJobLauncherOutPort;
import com.unip.fraud.application.port.out.repository.ImportJobRepositoryOutPort;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class ImportJobLauncherAdapter implements ImportJobLauncherOutPort {

  private final JobOperator jobOperator;
  private final Job datasetImportJob;
  private final ImportJobRepositoryOutPort repository;
  private final JobRepository jobRepository;

  public ImportJobLauncherAdapter(
      final JobOperator jobOperator,
      final @Qualifier("datasetImportJob") Job datasetImportJob,
      final ImportJobRepositoryOutPort repository,
      final JobRepository jobRepository) {
    this.jobOperator = jobOperator;
    this.datasetImportJob = datasetImportJob;
    this.repository = repository;
    this.jobRepository = jobRepository;
  }

  @Async
  @Override
  public void launch(final ImportJob importJob) {
    try {
      final JobExecution execution = jobOperator.start(datasetImportJob,
          new JobParametersBuilder()
              .addString("importId", importJob.id().toString())
              .addString("filePath", importJob.storedPath())
              .addString("originalFileName", importJob.fileName())
              .addLong("requestedAt", System.currentTimeMillis())
              .toJobParameters()
      );
      repository.markBatchExecutionId(importJob.id(), execution.getId());
    } catch (Exception exception) {
      repository.markFinished(
          importJob.id(),
          ImportStatus.FAILED,
          0,
          0,
          exception.getMessage(),
          LocalDateTime.now()
      );
    }
  }

  @Async
  @Override
  public void restart(final ImportJob importJob) {
    try {
      final JobExecution previous = Optional.ofNullable(
          jobRepository.getJobExecution(importJob.batchExecutionId())
      ).orElseThrow(() -> new IllegalStateException("Batch execution not found"));
      final JobExecution execution = jobOperator.restart(previous);
      repository.markBatchExecutionId(importJob.id(), execution.getId());
    } catch (Exception exception) {
      repository.markFinished(
          importJob.id(),
          ImportStatus.FAILED,
          importJob.processedRows(),
          importJob.rejectedRows(),
          exception.getMessage(),
          LocalDateTime.now()
      );
    }
  }
}
