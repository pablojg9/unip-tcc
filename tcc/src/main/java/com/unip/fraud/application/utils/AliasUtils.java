package com.unip.fraud.application.utils;

import java.util.List;
import java.util.Map;

public final class AliasUtils {

  public static final Map<String, List<String>> ALIASES = Map.of(
      "Time", List.of("time", "hora", "hour", "timestamp"),
      "Amount", List.of("amount", "valor", "value", "transaction_value", "preco"),
      "Class", List.of("class", "fraude", "is_fraud", "fraud", "label")
  );
}
