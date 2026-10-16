package com.unip.fraud.adapter.out.persistence.adapter;

import com.unip.fraud.adapter.out.persistence.mapper.GoldEntityMapper;
import com.unip.fraud.adapter.out.persistence.repository.GoldFraudResultRepository;
import com.unip.fraud.adapter.out.persistence.specification.FraudResultSpecificationFactory;
import com.unip.fraud.application.domain.FraudResult;
import com.unip.fraud.application.domain.FraudResultFilter;
import com.unip.fraud.application.domain.PageResponse;
import com.unip.fraud.application.domain.ConfusionMatrix;
import com.unip.fraud.application.domain.DecisionMetrics;
import com.unip.fraud.application.domain.ReasonMetric;
import com.unip.fraud.application.port.out.repository.GoldRepositoryOutPort;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

@Component
public class GoldPersistenceAdapter implements GoldRepositoryOutPort {

  private final GoldFraudResultRepository goldFraudResultRepository;
  private final GoldEntityMapper mapper;
  private final FraudResultSpecificationFactory specificationFactory;
  private final EntityManager entityManager;

  public GoldPersistenceAdapter(
      final GoldFraudResultRepository goldFraudResultRepository,
      final GoldEntityMapper mapper,
      final FraudResultSpecificationFactory specificationFactory,
      final EntityManager entityManager) {
    this.goldFraudResultRepository = goldFraudResultRepository;
    this.mapper = mapper;
    this.specificationFactory = specificationFactory;
    this.entityManager = entityManager;
  }

  @Override
  public void save(final FraudResult result) {
    goldFraudResultRepository.save(mapper.toEntity(result));
  }

  @Override
  public long count() {
    return goldFraudResultRepository.count();
  }

  @Override
  public long countRealFraud() {
    return goldFraudResultRepository.countByRealFraudTrue();
  }

  @Override
  public long countPredictedFraud() {
    return goldFraudResultRepository.countByPredictedFraudTrue();
  }

  @Override
  public long countPredictedLegitimate() {
    return goldFraudResultRepository.countByPredictedFraudFalse();
  }

  @Override
  public long countByRiskLevel(final String riskLevel) {
    return goldFraudResultRepository.countByRiskLevel(riskLevel);
  }

  @Override
  public long countPendingPrediction() {
    return goldFraudResultRepository.countByPredictedFraudIsNull();
  }

  @Override
  public long countPendingReview() {
    return goldFraudResultRepository.countByRealFraudIsNull();
  }

  @Override
  @Transactional(readOnly = true)
  public DecisionMetrics getDecisionMetrics(final boolean predictedFraud) {
    final Object[] row = (Object[]) entityManager.createNativeQuery("""
        SELECT
          COUNT(*),
          COALESCE(AVG(risk_score), 0),
          COUNT(*) FILTER (WHERE risk_level = 'LOW'),
          COUNT(*) FILTER (WHERE risk_level = 'MEDIUM'),
          COUNT(*) FILTER (WHERE risk_level = 'HIGH')
        FROM gold.fraud_prediction
        WHERE predicted_fraud = :predictedFraud
        """)
        .setParameter("predictedFraud", predictedFraud)
        .getSingleResult();

    final long total = number(row[0]).longValue();
    final long allPredictions = count();
    final String predominantModel = String.valueOf(entityManager.createNativeQuery("""
        SELECT COALESCE(model_version, 'Não informado')
        FROM gold.fraud_prediction
        WHERE predicted_fraud = :predictedFraud
        GROUP BY model_version
        ORDER BY COUNT(*) DESC, model_version NULLS LAST
        LIMIT 1
        """)
        .setParameter("predictedFraud", predictedFraud)
        .getResultList()
        .stream()
        .findFirst()
        .orElse("Não informado"));

    @SuppressWarnings("unchecked")
    final List<Object[]> reasonRows = entityManager.createNativeQuery("""
        SELECT reason.value, COUNT(*)
        FROM gold.fraud_prediction prediction
        CROSS JOIN LATERAL jsonb_array_elements_text(prediction.reasons) AS reason(value)
        WHERE prediction.predicted_fraud = :predictedFraud
          AND BTRIM(reason.value) <> ''
        GROUP BY reason.value
        ORDER BY COUNT(*) DESC, reason.value
        LIMIT 4
        """)
        .setParameter("predictedFraud", predictedFraud)
        .getResultList();
    final List<ReasonMetric> topReasons = reasonRows.stream()
        .map(reason -> new ReasonMetric(
            String.valueOf(reason[0]),
            number(reason[1]).longValue()
        ))
        .toList();

    return new DecisionMetrics(
        predictedFraud,
        total,
        percentage(total, allPredictions),
        number(row[1]).doubleValue(),
        number(row[2]).longValue(),
        number(row[3]).longValue(),
        number(row[4]).longValue(),
        predominantModel,
        topReasons
    );
  }

  @Override
  @Transactional(readOnly = true)
  public ConfusionMatrix getConfusionMatrix() {
    final Object[] row = (Object[]) entityManager.createNativeQuery("""
        SELECT
          COUNT(*) FILTER (WHERE predicted_fraud = TRUE AND confirmed_fraud = TRUE),
          COUNT(*) FILTER (WHERE predicted_fraud = TRUE AND confirmed_fraud = FALSE),
          COUNT(*) FILTER (WHERE predicted_fraud = FALSE AND confirmed_fraud = FALSE),
          COUNT(*) FILTER (WHERE predicted_fraud = FALSE AND confirmed_fraud = TRUE)
        FROM gold.fraud_prediction
        WHERE predicted_fraud IS NOT NULL AND confirmed_fraud IS NOT NULL
        """)
        .getSingleResult();
    final long truePositive = number(row[0]).longValue();
    final long falsePositive = number(row[1]).longValue();
    final long trueNegative = number(row[2]).longValue();
    final long falseNegative = number(row[3]).longValue();
    final long evaluated = truePositive + falsePositive + trueNegative + falseNegative;
    final double precision = ratio(truePositive, truePositive + falsePositive);
    final double recall = ratio(truePositive, truePositive + falseNegative);

    return new ConfusionMatrix(
        truePositive,
        falsePositive,
        trueNegative,
        falseNegative,
        evaluated,
        ratio(truePositive + trueNegative, evaluated),
        precision,
        recall,
        precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall)
    );
  }

  @Override
  public PageResponse<FraudResult> findByFilter(
      final FraudResultFilter filter,
      final int page,
      final int size) {
    final Sort sort = "PENDING".equalsIgnoreCase(filter.reviewStatus())
        ? Sort.by(Sort.Direction.DESC, "probability")
            .and(Sort.by(Sort.Direction.DESC, "processedAt"))
        : Sort.by(Sort.Direction.DESC, "processedAt");
    final Pageable pageable = PageRequest.of(page, size, sort);

    final Page<FraudResult> result = goldFraudResultRepository.findAll(
        specificationFactory.create(filter),
        pageable
    ).map(mapper::toDomain);
    return new PageResponse<>(
        result.getContent(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages()
    );
  }

  @Override
  public Optional<FraudResult> findByTransactionId(final String transactionId) {
    return goldFraudResultRepository.findById(transactionId).map(mapper::toDomain);
  }

  @Override
  public void updateConfirmedFraud(
      final String transactionId,
      final Boolean confirmedFraud) {
    goldFraudResultRepository.findById(transactionId).ifPresent(entity -> {
      entity.confirmFraud(confirmedFraud);
      goldFraudResultRepository.save(entity);
    });
  }

  private Number number(final Object value) {
    return value instanceof Number number ? number : 0;
  }

  private double percentage(final long value, final long total) {
    return total == 0 ? 0 : value * 100.0 / total;
  }

  private double ratio(final long value, final long total) {
    return total == 0 ? 0 : value * 1.0 / total;
  }
}
