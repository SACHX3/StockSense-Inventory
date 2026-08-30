"""Additional FastAPI router tests.

Run from fastapi-service with: pytest tests/ -v
"""
import asyncio
import os
import sys
from unittest.mock import patch

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from routers.forecast_router import ForecastRequest, RetrainRequest, forecast_status, predict_demand, retrain
from routers.ocr_router import OcrRequest, ocr_status, process_invoice


def run(coro):
    return asyncio.run(coro)


def test_forecast_router_returns_service_result():
    expected = {"status": "success", "product_id": 3, "predictions": []}
    with patch("routers.forecast_router.generate_forecast", return_value=expected) as generate:
        result = run(predict_demand(ForecastRequest(product_id=3, forecast_days=7, sales_history=[])))

    assert result == expected
    generate.assert_called_once_with(3, 7, [])


def test_forecast_retrain_router_returns_service_result():
    expected = {"status": "success", "message": "Models retrained"}
    with patch("routers.forecast_router.retrain_model", return_value=expected):
        assert run(retrain(RetrainRequest())) == expected


def test_forecast_status_reports_dependency_state():
    result = run(forecast_status())

    assert result["service"] == "Forecasting"
    assert "available" in result


def test_ocr_router_passes_invoice_request_to_service():
    expected = {"status": "completed", "invoice_id": 11, "items": []}
    with patch("routers.ocr_router.ocr_service.process_invoice", return_value=expected) as process:
        result = run(process_invoice(OcrRequest(
            invoice_id=11, file_path="uploads/invoices/invoice.pdf", file_type="PDF")))

    assert result == expected
    process.assert_called_once_with(11, "uploads/invoices/invoice.pdf", "PDF")


def test_ocr_status_exposes_dependency_flags():
    result = run(ocr_status())

    assert result["service"] == "OCR"
    assert "tesseract" in result
    assert "pdfplumber" in result
