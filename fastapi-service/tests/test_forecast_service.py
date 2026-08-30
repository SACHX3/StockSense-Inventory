"""Additional tests for statistical and Random Forest forecast behaviour.

Run from fastapi-service with: pytest tests/ -v
"""
import os
import sys
from unittest.mock import patch

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from services import forecast_service


def test_empty_history_uses_statistical_fallback():
    result = forecast_service.generate_forecast(4, forecast_days=5, sales_history=[])

    assert result["status"] == "success"
    assert result["model"] == "statistical_moving_average"
    assert result["training_samples"] == 0
    assert len(result["predictions"]) == 5


def test_fallback_predictions_have_valid_confidence_bounds():
    result = forecast_service.generate_forecast(4, forecast_days=10)

    for prediction in result["predictions"]:
        assert prediction["predicted_demand"] >= 0
        assert prediction["confidence_lower"] >= 0
        assert prediction["confidence_lower"] <= prediction["predicted_demand"]
        assert prediction["predicted_demand"] <= prediction["confidence_upper"]


def test_zero_forecast_days_returns_empty_prediction_list():
    result = forecast_service.generate_forecast(1, forecast_days=0)

    assert result["status"] == "success"
    assert result["predictions"] == []


def test_forecast_is_repeatable_for_same_product_and_inputs():
    first = forecast_service.generate_forecast(12, forecast_days=4)
    second = forecast_service.generate_forecast(12, forecast_days=4)

    assert first["predictions"] == second["predictions"]


def test_random_forest_is_selected_when_history_has_more_than_five_rows():
    history = [{"quantity": i + 1} for i in range(8)]

    if not forecast_service.SKLEARN_AVAILABLE:
        return

    result = forecast_service.generate_forecast(22, forecast_days=3, sales_history=history)

    assert result["status"] == "success"
    assert result["model"] == "random_forest"
    assert result["training_samples"] == 8
    assert len(result["predictions"]) == 3


def test_ml_failure_falls_back_to_statistical_forecast():
    with patch.object(forecast_service, "SKLEARN_AVAILABLE", True), \
         patch.object(forecast_service, "_ml_forecast", side_effect=RuntimeError("model error")):
        result = forecast_service.generate_forecast(6, forecast_days=2, sales_history=[{"quantity": 2}] * 8)

    assert result["status"] == "success"
    assert result["model"] == "statistical_moving_average"


def test_retrain_model_returns_success_status_and_timestamp():
    result = forecast_service.retrain_model()

    assert result["status"] == "success"
    assert result["message"] == "Models retrained"
    assert result["timestamp"]
