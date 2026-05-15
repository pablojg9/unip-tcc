package com.unip.fraud.application.utils;

import com.unip.fraud.application.domain.DatasetRecord;
import com.unip.fraud.application.domain.ParsedCsvRow;

import java.util.HashMap;
import java.util.Map;

public final class ParseUtils {

  public static ParsedCsvRow parse(final DatasetRecord record) {

    final Map<String, String> row = new HashMap<>();

    for (int i = 0; i < record.columns().length; i++) {
      row.put(normalize(record.columns()[i]), record.values()[i]);
    }

    return new ParsedCsvRow(row);
  }

  public static String normalize(final String value) {
    return value
        .toLowerCase()
        .trim()
        .replace(" ", "_")
        .replace("-", "_");
  }
}
