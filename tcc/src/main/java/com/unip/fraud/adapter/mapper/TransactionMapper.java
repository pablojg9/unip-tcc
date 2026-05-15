package com.unip.fraud.adapter.mapper;

import com.unip.fraud.application.domain.ParsedCsvRow;
import com.unip.fraud.application.domain.Transaction;
import com.unip.fraud.config.mapper.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Map;
import java.util.UUID;

import static java.util.Objects.isNull;

@Mapper(config = MapperConfiguration.class)
public interface TransactionMapper {

  @Mapping(target = "transactionId", expression = "java(generateTransactionId())")
  @Mapping(target = "realFraud", expression = "java(toBoolean(row.values().get(\"class\")))")
  @Mapping(target = "features", expression = "java(buildFeatures(row.values()))")
  Transaction toTransaction(final ParsedCsvRow row);

  @Named("generateTransactionId")
  default String generateTransactionId() {
    return UUID.randomUUID().toString();
  }

  @Named("toBoolean")
  default Boolean toBoolean(String value) {
    if (isNull(value)) {
      return false;
    }
    return value.equals("1")
        || value.equalsIgnoreCase("true")
        || value.equalsIgnoreCase("yes")
        || value.equalsIgnoreCase("fraud");
  }

  @Named("buildFeatures")
  default Map<String, Double> buildFeatures(final Map<String, String> row) {
    return Map.of("Time", Double.valueOf(row.getOrDefault("time", "0")),
        "Amount", Double.valueOf(row.getOrDefault("amount", "0")));
  }
}
