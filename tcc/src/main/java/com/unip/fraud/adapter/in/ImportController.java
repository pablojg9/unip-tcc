package com.unip.fraud.adapter.in;

import com.unip.fraud.application.domain.ImportFileCommand;
import com.unip.fraud.application.domain.ImportJobView;
import com.unip.fraud.application.domain.ImportScoringProgress;
import com.unip.fraud.application.domain.PageResponse;
import com.unip.fraud.application.port.in.GetImportsUseCase;
import com.unip.fraud.application.port.in.ImportDatasetUseCase;
import com.unip.fraud.application.service.ImportScoringProgressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/imports")
public class ImportController {

  private final ImportDatasetUseCase importDatasetUseCase;
  private final GetImportsUseCase getImportsUseCase;
  private final ImportScoringProgressService scoringProgress;

  public ImportController(
      final ImportDatasetUseCase importDatasetUseCase,
      final GetImportsUseCase getImportsUseCase,
      final ImportScoringProgressService scoringProgress) {
    this.importDatasetUseCase = importDatasetUseCase;
    this.getImportsUseCase = getImportsUseCase;
    this.scoringProgress = scoringProgress;
  }

  @PostMapping
  public ResponseEntity<ImportJobView> upload(
      @RequestParam("file") final MultipartFile file) {
    final ImportFileCommand command = new ImportFileCommand(
        file.getOriginalFilename(),
        file.getSize(),
        file::getInputStream
    );
    return ResponseEntity.accepted().body(importDatasetUseCase.importDataset(command));
  }

  @GetMapping
  public PageResponse<ImportJobView> list(
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size) {
    return getImportsUseCase.getImports(page, size);
  }

  @GetMapping("/{importId}")
  public ImportJobView get(@PathVariable final UUID importId) {
    return getImportsUseCase.getImport(importId);
  }

  @GetMapping("/{importId}/scoring-progress")
  public ImportScoringProgress scoringProgress(@PathVariable final UUID importId) {
    return scoringProgress.get(importId);
  }

  @PostMapping("/{importId}/retry")
  public ResponseEntity<ImportJobView> retry(@PathVariable final UUID importId) {
    return ResponseEntity.accepted().body(importDatasetUseCase.retry(importId));
  }

  @PostMapping("/{importId}/cancel-scoring")
  public ImportJobView cancelScoring(@PathVariable final UUID importId) {
    return importDatasetUseCase.cancelScoring(importId);
  }
}
