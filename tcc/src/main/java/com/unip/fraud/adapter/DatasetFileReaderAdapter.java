package com.unip.fraud.adapter;

import com.unip.fraud.application.domain.DatasetRecord;
import com.unip.fraud.application.port.out.dataset.DatasetFileReaderOutPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Component
public class DatasetFileReaderAdapter implements DatasetFileReaderOutPort {

  private final String datasetFolderPath;

  public DatasetFileReaderAdapter(
      @Value("${dataset.folder-path}") final String datasetFolderPath) {
    this.datasetFolderPath = datasetFolderPath;
  }

  @Override
  public List<DatasetRecord> readCsvFiles() {

    final Path datasetFolder = Paths.get(datasetFolderPath);

    try (Stream<Path> files = Files.list(datasetFolder)) {

      return files
          .filter(path -> path.toString().endsWith(".csv"))
          .flatMap(this::readFile)
          .toList();

    } catch (Exception exception) {
      throw new RuntimeException(
          "Error reading dataset folder",
          exception
      );
    }
  }

  private Stream<DatasetRecord> readFile(final Path file) {
    try {
      List<String> lines = Files.readAllLines(file);

      if (lines.isEmpty()) {
        return Stream.empty();
      }

      final String fileName = file.getFileName().toString();

      final String[] columns =
          lines.getFirst().split(",");

      return lines.stream()
          .skip(1)
          .map(line -> new DatasetRecord(
              fileName,
              columns,
              line.split(","),
              line
          ));

    } catch (Exception exception) {

      throw new RuntimeException(
          "Error reading file: " + file,
          exception
      );
    }
  }
}
