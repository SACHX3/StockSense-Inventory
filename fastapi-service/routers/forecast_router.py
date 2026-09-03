"""
Forecast Router - AI demand forecasting endpoints
POST /api/forecast/predict  -> predict demand for a product
POST /api/forecast/retrain  -> retrain model with latest data
"""
import logging
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import List, Optional
from services.forecast_service import (
    generate_forecast, retrain_model, train_product_model, load_product_model,
)

logger = logging.getLogger(__name__)
router = APIRouter()


class ForecastRequest(BaseModel):
    product_id: int
    forecast_days: int = 30
    sales_history: Optional[List[dict]] = []


class RetrainRequest(BaseModel):
    sales_history: Optional[dict] = {}


@router.post("/predict")
def predict_demand(request: ForecastRequest):
    """Generate demand forecast for a product"""
    try:
        result = generate_forecast(request.product_id, request.forecast_days, request.sales_history)
        return result
    except Exception as e:
        logger.error(f"Forecast error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/retrain")
# Deliberately a plain `def`, not `async def`: FastAPI then runs it in a worker
# thread, so /health and /predict keep responding while training is in progress.
def retrain(request: RetrainRequest):
    """Retrain every product model from the supplied sales history.

    Body: {"sales_history": {"products": {"<id>": [{"date","quantity"}, ...]}}}
    """
    try:
        return retrain_model(request.sales_history)
    except Exception as e:
        logger.error(f"Retrain error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/train/{product_id}")
def train_one(product_id: int, request: ForecastRequest = None):
    """Train (and persist) the model for a single product."""
    try:
        history = request.sales_history if request else []
        return train_product_model(product_id, history)
    except Exception as e:
        logger.error(f"Train error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/model/{product_id}")
async def model_info(product_id: int):
    """Inspect the persisted model for a product: metrics, features, age."""
    payload = load_product_model(product_id)
    if payload is None:
        return {"product_id": product_id, "exists": False,
                "message": "No trained model yet (or it predates the current feature set)."}
    return {
        "product_id": product_id,
        "exists": True,
        "trained_at": payload.get("trained_at"),
        "history_days": payload.get("history_days"),
        "training_rows": payload.get("training_rows"),
        "mae": payload.get("mae"),
        "rmse": payload.get("rmse"),
        "baseline_mae": payload.get("baseline_mae"),
        "metrics_basis": "chronological holdout (last 20% of days)",
        "feature_importances": payload.get("feature_importances"),
    }


@router.get("/status")
async def forecast_status():
    """Check forecasting service status"""
    try:
        import sklearn
        import numpy
        import pandas
        return {
            "service": "Forecasting",
            "available": True,
            "libraries": {
                "scikit-learn": sklearn.__version__,
                "numpy": numpy.__version__,
                "pandas": pandas.__version__
            }
        }
    except Exception as e:
        return {"service": "Forecasting", "available": False, "error": str(e)}
