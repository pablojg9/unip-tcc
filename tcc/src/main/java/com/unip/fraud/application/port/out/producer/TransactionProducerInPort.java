package com.unip.fraud.application.port.out.producer;

import com.unip.fraud.application.domain.Transaction;

public interface TransactionProducerInPort {
  void send(final Transaction transaction);
}
