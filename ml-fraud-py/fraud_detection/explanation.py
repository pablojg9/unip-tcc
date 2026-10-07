from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Protocol
from urllib.request import Request, urlopen

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class ExplanationContext:
  probability: float
  risk_level: str
  classification: str
  score_type: str
  threshold: float
  reasons: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class GeneratedExplanation:
  text: str
  explanation_type: str
  model: str | None


class ExplanationGenerator(Protocol):
  def generate(self, context: ExplanationContext) -> GeneratedExplanation: ...


class DeterministicExplanationGenerator:
  def __init__(self, explanation_type: str = "DETERMINISTIC") -> None:
    self._explanation_type = explanation_type

  def generate(self, context: ExplanationContext) -> GeneratedExplanation:
    evidence = " ".join(context.reasons)
    text = (
      f"O modelo classificou o sinistro como {context.risk_level.lower()}, "
      f"com score de {context.probability * 100:.1f}%. {evidence} "
      "O resultado serve como apoio e deve ser confirmado por revisão humana."
    )
    return GeneratedExplanation(
      text=_clean_text(text),
      explanation_type=self._explanation_type,
      model=None,
    )


class OllamaExplanationGenerator:
  def __init__(self, url: str, model: str, timeout_seconds: float) -> None:
    self._url = url
    self._model = model
    self._timeout_seconds = timeout_seconds

  def generate(self, context: ExplanationContext) -> GeneratedExplanation:
    payload = json.dumps({
      "model": self._model,
      "stream": False,
      "prompt": _prompt(context),
      "options": {"temperature": 0.1, "num_predict": 180},
    }, ensure_ascii=False).encode("utf-8")
    request = Request(
      self._url,
      data=payload,
      headers={"Content-Type": "application/json"},
      method="POST",
    )
    with urlopen(request, timeout=self._timeout_seconds) as response:
      result = json.loads(response.read().decode("utf-8"))
    text = _clean_text(str(result.get("response", "")))
    if not text:
      raise ValueError("The generative model returned an empty explanation")
    return GeneratedExplanation(
      text=text,
      explanation_type="GENERATIVE",
      model=self._model,
    )


class ResilientExplanationGenerator:
  def __init__(
      self,
      primary: ExplanationGenerator,
      fallback: ExplanationGenerator | None = None,
  ) -> None:
    self._primary = primary
    self._fallback = fallback or DeterministicExplanationGenerator("FALLBACK")

  def generate(self, context: ExplanationContext) -> GeneratedExplanation:
    try:
      return self._primary.generate(context)
    except Exception as exception:  # Explanation must never interrupt fraud scoring.
      logger.warning("Generative explanation unavailable: %s", exception)
      return self._fallback.generate(context)


def _prompt(context: ExplanationContext) -> str:
  evidence = "\n".join(f"- {reason}" for reason in context.reasons)
  return f"""
Você explica resultados de um modelo de risco de fraude automotiva em português do Brasil.
Escreva no máximo três frases curtas, claras e profissionais.
Use somente as evidências fornecidas. Não invente causas, regras ou dados.
Não afirme que houve fraude: explique risco e recomende revisão humana.

Tipo de score: {context.score_type}
Nível de risco: {context.risk_level}
Score: {context.probability * 100:.1f}%
Limiar: {context.threshold * 100:.1f}%
Classificação do modelo: {context.classification}
Evidências locais:
{evidence}
""".strip()


def _clean_text(value: str, limit: int = 2000) -> str:
  return " ".join(value.split())[:limit].strip()
