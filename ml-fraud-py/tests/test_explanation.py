import json
import unittest
from unittest.mock import MagicMock, patch

from fraud_detection.explanation import (
  ExplanationContext,
  OllamaExplanationGenerator,
  ResilientExplanationGenerator,
)


class ExplanationTest(unittest.TestCase):
  def setUp(self) -> None:
    self.context = ExplanationContext(
      probability=0.82,
      risk_level="HIGH",
      classification="Suspicious transaction",
      score_type="FRAUD_PROBABILITY",
      threshold=0.5,
      reasons=("Valor do sinistro aumentou o risco em 12 pontos percentuais.",),
    )

  @patch("fraud_detection.explanation.urlopen")
  def test_generates_explanation_with_ollama_contract(self, urlopen: MagicMock) -> None:
    response = MagicMock()
    response.read.return_value = json.dumps({
      "response": "O valor do sinistro elevou o risco. Recomenda-se revisão humana."
    }).encode()
    urlopen.return_value.__enter__.return_value = response
    generator = OllamaExplanationGenerator(
      "http://ollama:11434/api/generate", "llama3.2:1b", 2
    )

    result = generator.generate(self.context)

    self.assertEqual("GENERATIVE", result.explanation_type)
    self.assertEqual("llama3.2:1b", result.model)
    self.assertIn("revisão humana", result.text)
    request = urlopen.call_args.args[0]
    payload = json.loads(request.data.decode())
    self.assertFalse(payload["stream"])
    self.assertIn("Use somente as evidências", payload["prompt"])

  def test_falls_back_without_interrupting_scoring(self) -> None:
    generator = ResilientExplanationGenerator(FailingGenerator())

    with self.assertLogs("fraud_detection.explanation", level="WARNING"):
      result = generator.generate(self.context)

    self.assertEqual("FALLBACK", result.explanation_type)
    self.assertIsNone(result.model)
    self.assertIn("82.0%", result.text)


class FailingGenerator:
  def generate(self, context):
    raise RuntimeError("offline")


if __name__ == "__main__":
  unittest.main()
