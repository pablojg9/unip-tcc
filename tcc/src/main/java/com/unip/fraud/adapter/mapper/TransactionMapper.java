package com.unip.fraud.adapter.mapper;

import com.unip.fraud.application.domain.ParsedCsvRow;
import com.unip.fraud.application.domain.Transaction;
import com.unip.fraud.config.mapper.MapperConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Mapper(config = MapperConfiguration.class)
public interface TransactionMapper {

  @Mapping(target = "transactionId", expression = "java(generateTransactionId())")
  @Mapping(target = "realFraud", expression = "java(toBoolean(row.values().get(\"fraudfound_p\")))")
  @Mapping(target = "features", expression = "java(buildFeatures(row.values()))")
  Transaction toTransaction(final ParsedCsvRow row);

  @Named("generateTransactionId")
  default String generateTransactionId() {
    return UUID.randomUUID().toString();
  }

  @Named("toBoolean")
  default Boolean toBoolean(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }

    return value.equals("1")
        || value.equalsIgnoreCase("true")
        || value.equalsIgnoreCase("yes")
        || value.equalsIgnoreCase("sim")
        || value.equalsIgnoreCase("fraud");
  }

  @Named("buildFeatures")
  default Map<String, Object> buildFeatures(Map<String, String> row) {
    Map<String, Object> features = new LinkedHashMap<>();

    features.put("Month", row.get("month"));
    features.put("WeekOfMonth", parseInteger(row.get("weekofmonth")));
    features.put("DayOfWeek", row.get("dayofweek"));
    features.put("Make", row.get("make"));
    features.put("AccidentArea", row.get("accidentarea"));
    features.put("Sex", row.get("sex"));
    features.put("Age", parseInteger(row.get("age")));
    features.put("Fault", row.get("fault"));
    features.put("VehiclePrice", row.get("vehicleprice"));

    return features;
  }

  default Integer parseInteger(String value) {
    if (value == null || value.isBlank()) {
      return 0;
    }

    return Integer.parseInt(value);
  }
}
