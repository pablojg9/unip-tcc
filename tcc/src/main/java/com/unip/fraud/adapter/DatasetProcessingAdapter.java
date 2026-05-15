package com.unip.fraud.adapter;

import com.unip.fraud.adapter.mapper.TransactionMapper;
import com.unip.fraud.application.domain.DatasetRecord;
import com.unip.fraud.application.domain.ParsedCsvRow;
import com.unip.fraud.application.domain.Transaction;
import com.unip.fraud.application.port.in.ProcessDatasetUseCase;
import com.unip.fraud.application.port.out.dataset.DatasetFileReaderOutPort;
import com.unip.fraud.application.port.out.producer.TransactionProducerInPort;
import com.unip.fraud.application.port.out.repository.BronzeRepositoryOutPort;
import com.unip.fraud.application.port.out.repository.RejectedRecordRepositoryOutPort;
import com.unip.fraud.application.port.out.repository.SilverRepositoryOutPort;
import com.unip.fraud.application.utils.ParseUtils;
import org.springframework.stereotype.Component;

@Component
public class DatasetProcessingAdapter implements ProcessDatasetUseCase {
  private final DatasetFileReaderOutPort datasetFileReaderOutPort;
  private final TransactionMapper transactionMapper;
  private final BronzeRepositoryOutPort bronzeRepositoryOutPort;
  private final SilverRepositoryOutPort silverRepositoryOutPort;
  private final RejectedRecordRepositoryOutPort rejectedRecordRepositoryOutPort;
  private final TransactionProducerInPort transactionProducerInPort;

  public DatasetProcessingAdapter(
      final DatasetFileReaderOutPort datasetFileReaderOutPort,
      final TransactionMapper transactionMapper,
      final BronzeRepositoryOutPort bronzeRepositoryOutPort,
      final SilverRepositoryOutPort silverRepositoryOutPort,
      final RejectedRecordRepositoryOutPort rejectedRecordRepositoryOutPort,
      final TransactionProducerInPort transactionProducerInPort) {
    this.datasetFileReaderOutPort = datasetFileReaderOutPort;
    this.transactionMapper = transactionMapper;
    this.bronzeRepositoryOutPort = bronzeRepositoryOutPort;
    this.silverRepositoryOutPort = silverRepositoryOutPort;
    this.rejectedRecordRepositoryOutPort = rejectedRecordRepositoryOutPort;
    this.transactionProducerInPort = transactionProducerInPort;
  }


  @Override
  public void processDatasets() {
    datasetFileReaderOutPort.readCsvFiles()
        .forEach(this::processRecord);
  }

  private void processRecord(final DatasetRecord record) {
    bronzeRepositoryOutPort.save(record.fileName(), record.rawLine());

    try {
      final ParsedCsvRow row = ParseUtils.parse(record);
      final Transaction transaction = transactionMapper.toTransaction(row);

      silverRepositoryOutPort.save(record.fileName(), transaction);
      transactionProducerInPort.send(transaction);

    } catch (Exception exception) {
      rejectedRecordRepositoryOutPort.save(
          record.fileName(),
          record.rawLine(),
          exception.getMessage()
      );
    }
  }
}
