"""
Main Responsibility: FastAPI HTTP surface for RapidOCR (health + recognize).

Internal Compose service only. Returns raw text plus optional axis-aligned boxes.
"""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import Any

import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from pydantic import BaseModel, Field
from rapidocr import RapidOCR

from app.preprocess import prepare_for_ocr


class BoundingBoxOut(BaseModel):
    """Axis-aligned box for one OCR line (min/max of the RapidOCR polygon)."""

    x0: float
    y0: float
    x1: float
    y1: float


class OcrLineOut(BaseModel):
    text: str
    bbox: BoundingBoxOut | None = None


class OcrResponse(BaseModel):
    text: str
    lines: list[OcrLineOut] = Field(default_factory=list)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Load ONNX models once at startup so /health means the engine is ready.
    app.state.engine = RapidOCR()
    yield
    app.state.engine = None


app = FastAPI(title="finance-tracker-ocr", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    if getattr(app.state, "engine", None) is None:
        raise HTTPException(status_code=503, detail="OCR engine not ready")
    return {"status": "ok"}


@app.post("/ocr", response_model=OcrResponse)
async def run_ocr(file: UploadFile = File(...)) -> OcrResponse:
    """
    Accept one image (JPEG/PNG/rasterized PDF page) and return OCR text + lines.
    """
    engine: RapidOCR | None = getattr(app.state, "engine", None)
    if engine is None:
        raise HTTPException(status_code=503, detail="OCR engine not ready")

    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="Empty image body")

    try:
        image = prepare_for_ocr(image_bytes)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    try:
        result = engine(image)
    except Exception as exc:
        raise HTTPException(status_code=502, detail="OCR engine failed") from exc

    return _to_response(result)


def _to_response(result: Any) -> OcrResponse:
    """Map RapidOCROutput (or empty) into the Java client contract."""
    txts = getattr(result, "txts", None) or ()
    boxes = getattr(result, "boxes", None)

    lines: list[OcrLineOut] = []
    for index, text in enumerate(txts):
        line_text = (text or "").strip()
        if not line_text:
            continue
        bbox = _axis_aligned_bbox(boxes, index) if boxes is not None else None
        lines.append(OcrLineOut(text=line_text, bbox=bbox))

    joined = "\n".join(line.text for line in lines)
    return OcrResponse(text=joined, lines=lines)


def _axis_aligned_bbox(boxes: np.ndarray, index: int) -> BoundingBoxOut | None:
    """Convert one RapidOCR polygon (4×2) into min/max x/y for BoundingBox."""
    try:
        polygon = boxes[index]
        xs = [float(point[0]) for point in polygon]
        ys = [float(point[1]) for point in polygon]
        return BoundingBoxOut(x0=min(xs), y0=min(ys), x1=max(xs), y1=max(ys))
    except (IndexError, TypeError, ValueError):
        return None
