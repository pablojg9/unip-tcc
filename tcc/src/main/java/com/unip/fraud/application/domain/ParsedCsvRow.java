package com.unip.fraud.application.domain;

import java.util.Map;

public record ParsedCsvRow(
    Map<String, String> values
) {
}
