"""
Main Responsibility: Light image prep before RapidOCR (orientation + contrast).

Keeps OpenCV/Pillow work in the sidecar so the Java backend only sends bytes.
"""

from __future__ import annotations

import io

import numpy as np
from PIL import Image, ImageOps


def prepare_for_ocr(image_bytes: bytes) -> np.ndarray:
    """
    Decode image bytes, fix EXIF orientation, boost contrast, return RGB array.

    Raises ValueError when the bytes are not a readable image.
    """
    try:
        with Image.open(io.BytesIO(image_bytes)) as image:
            # Honor camera EXIF rotation so phone photos are upright for OCR.
            oriented = ImageOps.exif_transpose(image)
            rgb = oriented.convert("RGB")
            # Gentle autocontrast helps washed-out receipt photos without inventing pixels.
            enhanced = ImageOps.autocontrast(rgb, cutoff=2)
            return np.asarray(enhanced)
    except Exception as exc:  # Pillow raises many subclasses for bad input
        raise ValueError("Could not decode image bytes for OCR") from exc
