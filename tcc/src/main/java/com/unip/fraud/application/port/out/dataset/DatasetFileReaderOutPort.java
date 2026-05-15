package com.unip.fraud.application.port.out.dataset;

import com.unip.fraud.application.domain.DatasetRecord;

import java.util.List;

public interface DatasetFileReaderOutPort {
  List<DatasetRecord> readCsvFiles();
}
