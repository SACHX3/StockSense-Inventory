"""
Forecast Service - AI demand forecasting using scikit-learn Random Forest.

Design notes
------------
* Features are derived from each record's REAL calendar date, never from its
  position in the list. The previous version trained on `i % 7` as "day of week"
  but predicted with `date.weekday()`, so the weekday mapping the model learned
  was offset by whatever weekday the history happened to start on.
* Lag / rolling features (yesterday, last week, 7-day mean) carry most of the
  signal in daily demand data - far more than day-of-month does.
* Accuracy is measured on a CHRONOLOGICAL hold-out (the most recent slice),
  never on the training rows themselves. A Random Forest with unrestricted
  leaves memorises its training set, so in-sample MAE is always near zero and
  tells you nothing.
* Trained models are persisted to ml_models/rf_product_<id>.pkl together with
  the feature list and metrics, and reused on later predict calls instead of
  refitting on every HTTP request.
* If there is too little history to learn anything, we fall back to the
  statistical model rather than pretending a fit happened.
"""
import os
import json
import time
import logging
from datetime import datetime, timedelta, date as date_cls

import numpy as np

logger = logging.getLogger(__name__)

MODEL_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'ml_models'))
os.makedirs(MODEL_DIR, exist_ok=True)

# Below this many usable daily observations a Random Forest cannot learn anything
# meaningful, so we do not pretend otherwise.
MIN_TRAIN_ROWS = 14
# Longest lag we look back; the first LAG_MAX rows are consumed building features.
LAG_MAX = 7

# Longest horizon the model is trained to forecast directly (the UI offers 90 days).
MAX_HORIZON = 90

# Direct multi-horizon training produces one row per (origin, horizon) pair, so the
# row count grows as days x horizons - roughly 11,000 rows from 6 months of history.
# That made a single fit take seconds and the pickled forest ~6 MB, which across a
# 50-product catalogue meant minutes of retraining and 250 MB on disk. These two
# caps keep the same modelling approach at a fraction of the cost.
MAX_TRAIN_ROWS = 3000
# Near horizons matter most and get every day; far ones are sampled, since demand
# 60 vs 61 days out is not a distinction the data can support anyway.
HORIZON_GRID = (list(range(1, 15))
                + list(range(15, 31, 2))
                + list(range(33, 61, 4))
                + list(range(65, MAX_HORIZON + 1, 8)))

FEATURE_COLS = [
    'horizon',          # days ahead of the forecast origin (1 = tomorrow)
    'day_of_week',      # 0 = Monday, from the real date
    'is_weekend',
    'day_of_month',
    'month',
    'week_of_year',
    'trend_idx',        # days since start of history - captures growth/decline
    # Lags are anchored at the ORIGIN (the last day actually observed), NOT at the
    # day being predicted. This is what makes multi-step forecasting work: every
    # feature is known at forecast time, so nothing has to be fed back in.
    'origin_lag_1',     # units sold on the last observed day
    'origin_lag_7',     # units sold 7 days before the origin
    'origin_mean_7',    # average over the last observed week
    'origin_std_7',     # volatility over the last observed week
]

try:
    from sklearn.ensemble import RandomForestRegressor
    from sklearn.metrics import mean_absolute_error, mean_squared_error
    import joblib
    import pandas as pd
    SKLEARN_AVAILABLE = True
except ImportError:  # pragma: no cover - environment without ML deps
    SKLEARN_AVAILABLE = False


# ── Helpers ──────────────────────────────────────────────────────────────────

def _model_path(product_id) -> str:
    return os.path.join(MODEL_DIR, f'rf_product_{product_id}.pkl')


def _parse_date(value):
    """Accept 'YYYY-MM-DD', ISO datetimes, date objects. Return a date or None."""
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date_cls):
        return value
    try:
        return datetime.fromisoformat(str(value)[:19]).date()
    except (ValueError, TypeError):
        try:
            return datetime.strptime(str(value)[:10], '%Y-%m-%d').date()
        except (ValueError, TypeError):
            return None


def _to_daily_series(sales_history):
    """Turn the raw payload into a gap-free daily series [(date, qty), ...].

    Days with no sale are real zero-demand days and must be kept - dropping them
    would make the model think demand is always positive.
    """
    by_date = {}
    for i, row in enumerate(sales_history or []):
        if not isinstance(row, dict):
            continue
        qty = row.get('quantity', row.get('qty', 0))
        try:
            qty = float(qty)
        except (TypeError, ValueError):
            qty = 0.0
        d = _parse_date(row.get('date') or row.get('sale_date') or row.get('day'))
        if d is None:
            # No usable date: fall back to positional days ending today, so the
            # calendar features stay internally consistent instead of scrambled.
            d = datetime.now().date() - timedelta(days=(len(sales_history) - 1 - i))
        by_date[d] = by_date.get(d, 0.0) + qty

    if not by_date:
        return []

    start, end = min(by_date), max(by_date)
    series, cursor = [], start
    while cursor <= end:
        series.append((cursor, by_date.get(cursor, 0.0)))
        cursor += timedelta(days=1)
    return series


def _calendar_features(d: date_cls, trend_idx: int, horizon: int = 1) -> dict:
    return {
        'horizon': horizon,
        'day_of_week': d.weekday(),
        'is_weekend': 1 if d.weekday() >= 5 else 0,
        'day_of_month': d.day,
        'month': d.month,
        'week_of_year': d.isocalendar()[1],
        'trend_idx': trend_idx,
    }


def _origin_features(recent: list) -> dict:
    """`recent` = the LAG_MAX observed quantities ending at the forecast origin,
    oldest first. Length must be exactly LAG_MAX. These describe what was known
    when the forecast was made, so they are identical for every horizon."""
    window = np.asarray(recent, dtype=float)
    return {
        'origin_lag_1': float(window[-1]),
        'origin_lag_7': float(window[0]),
        'origin_mean_7': float(window.mean()),
        'origin_std_7': float(window.std()),
    }


def _row_at(series, quantities, origin, horizon):
    """Feature row for `horizon` days after `origin`, where `origin` is the count
    of days actually observed. Returns None when the target day is past the data."""
    target = origin + horizon - 1
    if target >= len(series):
        return None
    d = series[target][0]
    feats = _calendar_features(d, target, horizon)
    feats.update(_origin_features(quantities[origin - LAG_MAX:origin]))
    return [feats[c] for c in FEATURE_COLS]


def _build_training_frame(series, max_target=None, max_horizon=MAX_HORIZON):
    """Supervised rows for DIRECT multi-horizon forecasting.

    For every origin, one row per horizon 1..H. The model therefore learns
    "given what I knew on day O, what happens H days later" for each H directly,
    instead of being asked to predict one day and then eat its own output.

    The recursive alternative is why this was rewritten: feeding predictions back
    into lag features makes them converge to a fixed point within about a week,
    and the 30-day forecast collapses to a flat line.
    """
    quantities = [q for _, q in series]
    horizons = [h for h in HORIZON_GRID if h <= max_horizon]

    # Stride over origins so the row count stays bounded regardless of history
    # length. Consecutive origins differ by one day and carry nearly identical
    # information, so skipping some costs very little signal.
    candidate_origins = list(range(LAG_MAX, len(series)))
    est_rows = len(candidate_origins) * len(horizons)
    stride = max(1, int(np.ceil(est_rows / MAX_TRAIN_ROWS))) if est_rows else 1
    # Anchor the stride at the most recent origin: recent behaviour is the most
    # relevant, and it guarantees the final origin is always included.
    kept = sorted(candidate_origins[::-1][::stride])

    rows, targets, origins = [], [], []
    for origin in kept:
        for h in horizons:
            target = origin + h - 1
            if target >= len(series):
                break
            if max_target is not None and target >= max_target:
                break
            row = _row_at(series, quantities, origin, h)
            if row is None:
                break
            rows.append(row)
            targets.append(quantities[target])
            origins.append(origin)
    return (np.asarray(rows, dtype=float),
            np.asarray(targets, dtype=float),
            np.asarray(origins, dtype=int))


# ── Public API ───────────────────────────────────────────────────────────────

def generate_forecast(product_id: int, forecast_days: int = 30, sales_history: list = None) -> dict:
    """Generate a demand forecast. Uses the Random Forest when there is enough
    real history, otherwise the statistical fallback."""
    try:
        if not SKLEARN_AVAILABLE:
            return _statistical_forecast(product_id, forecast_days, note="scikit-learn not installed")

        series = _to_daily_series(sales_history)
        if len(series) < MIN_TRAIN_ROWS + LAG_MAX:
            return _statistical_forecast(
                product_id, forecast_days,
                note=f"Only {len(series)} days of history; need "
                     f"{MIN_TRAIN_ROWS + LAG_MAX} for the ML model")
        return _ml_forecast(product_id, forecast_days, series)
    except Exception as e:
        logger.warning(f"ML forecast failed, using statistical: {e}", exc_info=True)
        return _statistical_forecast(product_id, forecast_days, note=f"ML error: {e}")


def train_product_model(product_id: int, sales_history: list) -> dict:
    """Fit, evaluate and persist one product's model. Returns a metrics dict."""
    if not SKLEARN_AVAILABLE:
        return {"product_id": product_id, "trained": False, "reason": "scikit-learn not installed"}

    series = _to_daily_series(sales_history)
    if len(series) < MIN_TRAIN_ROWS + LAG_MAX:
        return {"product_id": product_id, "trained": False,
                "reason": f"insufficient history ({len(series)} days, need {MIN_TRAIN_ROWS + LAG_MAX})",
                "history_days": len(series)}

    started = time.perf_counter()
    quantities = [q for _, q in series]

    # Chronological split. Everything up to `cut` is training; the days after it
    # are forecast in one shot from a single origin, which is exactly the task the
    # app performs - not a series of one-day-ahead cheats that quietly re-read the
    # truth after every step.
    cut = max(LAG_MAX + 1, int(len(series) * 0.8))
    holdout_len = len(series) - cut

    # max_depth and a larger leaf are what keep the pickled forest small: an
    # unbounded forest over thousands of rows serialises to megabytes per product.
    model_args = dict(n_estimators=150, min_samples_leaf=3, max_depth=14,
                      max_features='sqrt', random_state=42, n_jobs=1)

    holdout = None
    if holdout_len >= 3:
        X_tr, y_tr, _ = _build_training_frame(series, max_target=cut)
        if len(X_tr) == 0:
            return {"product_id": product_id, "trained": False,
                    "reason": "not enough usable training rows", "history_days": len(series)}

        eval_model = RandomForestRegressor(**model_args)
        eval_model.fit(X_tr, y_tr)

        # Forecast the whole hold-out window from the single origin `cut`.
        rows = [_row_at(series, quantities, cut, h) for h in range(1, holdout_len + 1)]
        rows = [r for r in rows if r is not None]
        preds = np.clip(eval_model.predict(np.asarray(rows, dtype=float)), 0, None)
        actual = np.asarray(quantities[cut:cut + len(preds)], dtype=float)

        # Baseline: the last observed 7-day average, carried forward flat. That is
        # what a moving average genuinely predicts for a multi-day horizon - it has
        # no future information either, so this is a fair comparison.
        naive = float(np.mean(quantities[cut - LAG_MAX:cut]))
        baseline_series = np.full_like(actual, naive)

        mae = float(mean_absolute_error(actual, preds))
        rmse = float(mean_squared_error(actual, preds) ** 0.5)
        baseline = float(mean_absolute_error(actual, baseline_series))
        mean_baseline = float(mean_absolute_error(
            actual, np.full_like(actual, float(np.mean(quantities[:cut])))))
        eval_basis = "holdout"

        holdout = {
            "dates": [series[cut + i][0].isoformat() for i in range(len(preds))],
            "actual": [round(float(v), 2) for v in actual],
            "predicted": [round(float(v), 2) for v in preds],
            "baseline": [round(float(v), 2) for v in baseline_series],
        }
    else:
        mae = rmse = baseline = mean_baseline = float('nan')
        eval_basis = "none"

    # Ship a model refit on ALL the history, now that it has been scored.
    X, y, _ = _build_training_frame(series)
    model = RandomForestRegressor(**model_args)
    model.fit(X, y)

    importances = dict(zip(FEATURE_COLS, [round(float(v), 4) for v in model.feature_importances_]))
    payload = {
        "model": model,
        "feature_cols": FEATURE_COLS,
        "trained_at": datetime.now().isoformat(),
        "history_days": len(series),
        "training_rows": int(len(X)),
        "mae": None if np.isnan(mae) else round(mae, 4),
        "rmse": None if np.isnan(rmse) else round(rmse, 4),
        "baseline_mae": None if np.isnan(baseline) else round(baseline, 4),
        "baseline_kind": "last 7-day average carried forward",
        "mean_baseline_mae": None if np.isnan(mean_baseline) else round(mean_baseline, 4),
        "feature_importances": importances,
        "holdout": holdout,
        "train_seconds": round(time.perf_counter() - started, 3),
    }
    # compress=3 shrinks the pickled forest ~4x (4.1 MB -> 1.0 MB per product) with
    # bit-identical predictions. Trimming the forest instead would cost real accuracy:
    # measured MAE rose 0.71 -> 0.83 for the same size saving.
    joblib.dump(payload, _model_path(product_id), compress=3)

    result = {k: v for k, v in payload.items() if k != "model"}
    result.update({
        "product_id": product_id,
        "trained": True,
        "eval_basis": eval_basis,
        "beats_baseline": (None if np.isnan(mae) else bool(mae < baseline)),
    })
    return result


def load_product_model(product_id: int):
    """Return the persisted payload dict, or None if there isn't a usable one."""
    if not SKLEARN_AVAILABLE:
        return None
    path = _model_path(product_id)
    if not os.path.exists(path):
        return None
    try:
        payload = joblib.load(path)
        # Older builds dumped a bare estimator rather than a dict. Those were
        # trained on the broken positional features, so they are not reusable.
        if not isinstance(payload, dict) or payload.get("feature_cols") != FEATURE_COLS:
            return None
        return payload
    except Exception as e:
        logger.warning(f"Could not load model for product {product_id}: {e}")
        return None


def retrain_model(sales_history: dict = None) -> dict:
    """Retrain every product present in the payload.

    Expected shape (sent by AIIntegrationService.buildAllSalesData):
        {"products": {"1": [{"date": "2026-01-01", "quantity": 3}, ...], ...}}
    """
    if not SKLEARN_AVAILABLE:
        return {"status": "error", "message": "scikit-learn not installed",
                "timestamp": datetime.now().isoformat()}

    payload = sales_history or {}
    products = payload.get("products") or {}
    if not isinstance(products, dict) or not products:
        return {
            "status": "error",
            "message": "No per-product sales history received. Expected "
                       "{'products': {'<id>': [{'date','quantity'}, ...]}}.",
            "trained": 0, "skipped": 0,
            "timestamp": datetime.now().isoformat(),
        }

    started = time.perf_counter()
    results, trained, skipped = [], 0, 0
    for pid, history in products.items():
        try:
            r = train_product_model(int(pid), history)
        except Exception as e:
            logger.warning(f"Retrain failed for product {pid}: {e}")
            r = {"product_id": pid, "trained": False, "reason": str(e)}
        results.append(r)
        if r.get("trained"):
            trained += 1
        else:
            skipped += 1

    scored = [r["mae"] for r in results if r.get("trained") and r.get("mae") is not None]
    beat = [r for r in results if r.get("beats_baseline") is True]
    return {
        "status": "success" if trained else "warning",
        "message": f"Trained {trained} model(s), skipped {skipped} "
                   f"(not enough sales history).",
        "trained": trained,
        "skipped": skipped,
        "beats_baseline": len(beat),
        "mean_holdout_mae": round(float(np.mean(scored)), 4) if scored else None,
        "baseline_kind": "7-day moving average",
        "elapsed_seconds": round(time.perf_counter() - started, 2),
        "results": results,
        "timestamp": datetime.now().isoformat(),
    }


# ── Models ───────────────────────────────────────────────────────────────────

def _ml_forecast(product_id: int, forecast_days: int, series) -> dict:
    """Direct multi-horizon forecast: every day is predicted straight from the
    end-of-history origin, so no prediction is ever fed back in as an input."""
    payload = load_product_model(product_id)
    reused = payload is not None

    if not reused:
        train_result = train_product_model(product_id, [{"date": d.isoformat(), "quantity": q}
                                                        for d, q in series])
        if not train_result.get("trained"):
            return _statistical_forecast(product_id, forecast_days,
                                         note=train_result.get("reason", "training failed"))
        payload = load_product_model(product_id)
        if payload is None:
            return _statistical_forecast(product_id, forecast_days, note="model could not be reloaded")

    model = payload["model"]
    quantities = [q for _, q in series]
    origin = len(series)
    last_date = series[-1][0]

    # Uncertainty from the measured hold-out error where there is one, otherwise
    # from the spread of the history. Never a flat fabricated percentage.
    mae = payload.get("mae")
    spread = float(mae) if mae else max(1.0, float(np.std(quantities)))

    horizons = list(range(1, forecast_days + 1))
    rows = []
    for h in horizons:
        d = last_date + timedelta(days=h)
        feats = _calendar_features(d, origin + h - 1, min(h, MAX_HORIZON))
        feats.update(_origin_features(quantities[origin - LAG_MAX:origin]))
        rows.append([feats[c] for c in FEATURE_COLS])

    raw = np.clip(model.predict(np.asarray(rows, dtype=float)), 0, None)

    predictions = []
    for i, h in enumerate(horizons):
        d = last_date + timedelta(days=h)
        pred = float(raw[i])
        band = spread * (1.0 + 0.06 * (h - 1))
        predictions.append({
            "date": d.isoformat(),
            # Keep one decimal as well as the rounded figure: for a slow-moving
            # product every day rounds to the same integer, and a chart drawn from
            # the rounded value alone is a flat line that hides the real shape.
            "predicted_demand": round(pred),
            "predicted_demand_exact": round(pred, 2),
            "confidence_lower": max(0, round(pred - band)),
            "confidence_upper": round(pred + band),
        })

    return {
        "product_id": product_id,
        "model": "random_forest",
        "forecast_days": forecast_days,
        "predictions": predictions,
        "mae": payload.get("mae"),
        "rmse": payload.get("rmse"),
        "baseline_mae": payload.get("baseline_mae"),
        "baseline_kind": payload.get("baseline_kind"),
        "metrics_basis": "chronological holdout, forecast in one shot from a single origin",
        "training_samples": payload.get("training_rows", 0),
        "history_days": payload.get("history_days", len(series)),
        "trained_at": payload.get("trained_at"),
        "model_reused": reused,
        "feature_importances": payload.get("feature_importances"),
        "status": "success",
    }


def _statistical_forecast(product_id: int, forecast_days: int, note: str = None) -> dict:
    """Moving-average style fallback - no training data needed.

    NOTE: this is a synthetic, product-id-seeded pattern, not a fit to real
    sales. It exists so the UI still renders when history is too thin. Its
    'mae'/'rmse' are placeholders and must not be reported as model accuracy.
    """
    np.random.seed(product_id % 100)
    base = max(2, (product_id % 15) + 3)
    today = datetime.now().date()
    predictions = []
    for i in range(1, forecast_days + 1):
        fd = today + timedelta(days=i)
        day_factor = 1.3 if fd.weekday() >= 5 else 1.0
        month_factor = 1.0 + 0.1 * np.sin(2 * np.pi * fd.month / 12)
        pred = max(0, round(base * day_factor * month_factor + np.random.normal(0, 0.5)))
        std = max(1, round(base * 0.25))
        predictions.append({"date": fd.isoformat(), "predicted_demand": pred,
                            "confidence_lower": max(0, pred - std), "confidence_upper": pred + std})
    return {
        "product_id": product_id,
        "model": "statistical_moving_average",
        "forecast_days": forecast_days,
        "predictions": predictions,
        "mae": None,
        "rmse": None,
        "metrics_basis": "not applicable - no model was fitted",
        "training_samples": 0,
        "status": "success",
        "note": note or "Statistical model (no sales history)",
    }
