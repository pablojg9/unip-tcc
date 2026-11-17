package com.unip.fraud.application.port.in;

import com.unip.fraud.application.domain.ImportFileCommand;
import com.unip.fraud.application.domain.ImportJobView;

import java.util.UUID;

public interface ImportDatasetUseCase {
  ImportJobView importDataset(ImportFileCommand command);
  ImportJobView retry(UUID importId);
  ImportJobView cancelScoring(UUID importId);
}
