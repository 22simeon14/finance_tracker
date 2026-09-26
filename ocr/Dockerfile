# Main Responsibility: CPU RapidOCR sidecar image (FastAPI + ONNX Runtime).
# Models warm during build so container health means OCR is ready to serve.
# Warm uses the same Cyrillic Rec params as app.main.create_ocr_engine.

FROM python:3.12-slim

WORKDIR /app

# OpenCV (pulled by RapidOCR) needs these libs even for headless CPU inference.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# Download Cyrillic PP-OCRv5 Rec (+ default Det/Cls) into the image layer.
RUN python -c "from app.main import create_ocr_engine; create_ocr_engine()"

EXPOSE 8080

# Single worker: OCR is CPU/memory heavy; extra workers would duplicate models.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
