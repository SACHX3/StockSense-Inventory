"""
OCR Processor - Extracts text and structured data from invoice images and PDFs
Uses: pytesseract (images) + pdfplumber (PDFs)
"""
import re
import os
import logging
from pathlib import Path
from typing import Optional
from utils.tesseract_compat import load_pytesseract

logger = logging.getLogger(__name__)

# ── Try to import OCR libraries ──────────────────────────────────────────────
try:
    pytesseract = load_pytesseract()
    from PIL import Image, ImageEnhance, ImageFilter
    TESSERACT_AVAILABLE = True
    # Windows path - set tesseract executable
    if os.name == 'nt':
        tesseract_path = r'C:\Program Files\Tesseract-OCR\tesseract.exe'
        if os.path.exists(tesseract_path):
            pytesseract.pytesseract.tesseract_cmd = tesseract_path
except ImportError:
    TESSERACT_AVAILABLE = False
    logger.warning("pytesseract not available - image OCR disabled")

try:
    import pdfplumber
    PDFPLUMBER_AVAILABLE = True
except ImportError:
    PDFPLUMBER_AVAILABLE = False
    logger.warning("pdfplumber not available - PDF OCR disabled")


def preprocess_image(image_path: str):
    """Enhance image for better OCR accuracy"""
    img = Image.open(image_path).convert('L')          # Grayscale
    img = img.filter(ImageFilter.SHARPEN)               # Sharpen edges
    enhancer = ImageEnhance.Contrast(img)
    img = enhancer.enhance(2.0)                         # Boost contrast
    enhancer = ImageEnhance.Brightness(img)
    img = enhancer.enhance(1.1)                         # Slight brightness
    # Resize if too small
    w, h = img.size
    if w < 1000:
        img = img.resize((w * 2, h * 2), Image.LANCZOS)
    return img


def extract_text_from_image(image_path: str) -> str:
    """Extract raw text from invoice image using Tesseract OCR"""
    if not TESSERACT_AVAILABLE:
        raise RuntimeError("Tesseract OCR not installed. See setup instructions.")
    try:
        img = preprocess_image(image_path)
        # Use PSM 6 = single uniform block of text, good for invoices
        config = '--psm 6 --oem 3 -c tessedit_char_whitelist=0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz.,:-/() '
        text = pytesseract.image_to_string(img, config=config)
        return text.strip()
    except Exception as e:
        logger.error(f"Image OCR failed: {e}")
        raise RuntimeError(f"Image OCR failed: {str(e)}")


def extract_text_from_pdf(pdf_path: str) -> str:
    """Extract raw text from invoice PDF using pdfplumber"""
    if not PDFPLUMBER_AVAILABLE:
        raise RuntimeError("pdfplumber not installed. Run: pip install pdfplumber")
    try:
        full_text = []
        with pdfplumber.open(pdf_path) as pdf:
            for page in pdf.pages:
                text = page.extract_text()
                if text:
                    full_text.append(text)
        return "\n".join(full_text).strip()
    except Exception as e:
        logger.error(f"PDF text extraction failed: {e}")
        raise RuntimeError(f"PDF extraction failed: {str(e)}")


def parse_invoice_data(raw_text: str) -> dict:
    """
    Parse raw OCR text into structured invoice data.
    Extracts: invoice_number, date, supplier, items, total
    """
    result = {
        "invoice_number": None,
        "invoice_date": None,
        "supplier_name": None,
        "total_amount": None,
        "items": [],
        "raw_text": raw_text
    }

    lines = [l.strip() for l in raw_text.split('\n') if l.strip()]

    # ── Extract Invoice Number ──────────────────────────────────────────────
    inv_patterns = [
        r'(?:Invoice\s*No\.?|INV|Invoice\s*#|Invoice\s*Number)[:\s#]*([A-Z0-9\-/]+)',
        r'\b(INV[-/]\d{4}[-/]\d+)\b',
        r'\b(INV\d{6,})\b',
    ]
    for pattern in inv_patterns:
        match = re.search(pattern, raw_text, re.IGNORECASE)
        if match:
            result["invoice_number"] = match.group(1).strip()
            break

    # ── Extract Date ────────────────────────────────────────────────────────
    date_patterns = [
        r'(?:Invoice\s*Date|Date)[:\s]*(\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]\d{2,4})',
        r'(?:Invoice\s*Date|Date)[:\s]*(\d{1,2}\s+\w+\s+\d{4})',
        r'\b(\d{1,2}[\/\-]\d{1,2}[\/\-]\d{4})\b',
        r'\b(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\w*\s+\d{4})\b',
    ]
    for pattern in date_patterns:
        match = re.search(pattern, raw_text, re.IGNORECASE)
        if match:
            result["invoice_date"] = match.group(1).strip()
            break

    # ── Extract Supplier Name ───────────────────────────────────────────────
    supplier_patterns = [
        r'(?:Supplier|From|Vendor|Company)[:\s]+([A-Za-z][A-Za-z\s&\.,]+(?:Ltd|PLC|Inc|Co\.|Pvt)?)',
        r'^([A-Za-z][A-Za-z\s&\.,]+(?:Ltd|PLC|Inc|Co\.|Pvt)\.?)',
    ]
    for pattern in supplier_patterns:
        match = re.search(pattern, raw_text, re.IGNORECASE | re.MULTILINE)
        if match:
            name = match.group(1).strip()
            if len(name) > 3 and len(name) < 100:
                result["supplier_name"] = name
                break

    # ── Extract Total Amount ────────────────────────────────────────────────
    total_patterns = [
        r'(?:TOTAL\s*PAYABLE|GRAND\s*TOTAL|TOTAL\s*AMOUNT|AMOUNT\s*DUE)[:\s]*(?:Rs\.?|LKR|USD|\$)?\s*([\d,]+\.?\d*)',
        r'(?:TOTAL)[:\s]*(?:Rs\.?|LKR)?\s*([\d,]+\.?\d*)',
        r'(?:Rs\.?|LKR)\s*([\d,]+\.\d{2})\s*$',
    ]
    for pattern in total_patterns:
        match = re.search(pattern, raw_text, re.IGNORECASE | re.MULTILINE)
        if match:
            amount_str = match.group(1).replace(',', '').strip()
            try:
                result["total_amount"] = float(amount_str)
                break
            except ValueError:
                pass

    # ── Extract Line Items ──────────────────────────────────────────────────
    result["items"] = extract_line_items(raw_text, lines)

    return result


def extract_line_items(raw_text: str, lines: list) -> list:
    """
    Extract product line items from invoice text.
    Handles common invoice table formats.
    """
    items = []

    # Pattern: product name | qty | unit price | total
    # Works for both image and PDF extracted text
    item_pattern = re.compile(
        r'(\d+)\s+'                                    # Row number
        r'([A-Za-z][A-Za-z0-9\s\-\.]+?)\s+'          # Product name
        r'(?:(\w+)\s+)?'                               # Unit (optional)
        r'(\d+)\s+'                                    # Quantity
        r'([\d,]+\.?\d*)\s+'                          # Unit price
        r'([\d,]+\.?\d*)',                             # Total
        re.IGNORECASE
    )

    # Also try simpler pattern without row number
    item_pattern2 = re.compile(
        r'^([A-Za-z][A-Za-z0-9\s\-\.]{5,50}?)\s+'
        r'(\d{1,4})\s+'
        r'([\d,]+\.\d{2})\s+'
        r'([\d,]+\.\d{2})',
        re.IGNORECASE | re.MULTILINE
    )

    seen_products = set()

    # Try pattern 1 (numbered rows)
    for match in item_pattern.finditer(raw_text):
        product_name = match.group(2).strip()
        if product_name.lower() in seen_products:
            continue
        if len(product_name) < 3:
            continue
        # Filter out header/footer words
        skip_words = ['product', 'description', 'item', 'subtotal', 'total', 'discount', 'vat', 'tax']
        if any(w in product_name.lower() for w in skip_words):
            continue

        try:
            qty = int(match.group(4))
            unit_price = float(match.group(5).replace(',', ''))
            total_price = float(match.group(6).replace(',', ''))

            # Sanity check
            if qty <= 0 or qty > 10000:
                continue
            if unit_price <= 0 or unit_price > 1000000:
                continue

            confidence = calculate_confidence(product_name, qty, unit_price, total_price)

            items.append({
                "product_name": product_name,
                "unit": match.group(3) or "pcs",
                "quantity": qty,
                "unit_price": round(unit_price, 2),
                "total_price": round(total_price, 2),
                "confidence": confidence
            })
            seen_products.add(product_name.lower())
        except (ValueError, AttributeError):
            continue

    # If no items found with pattern 1, try pattern 2
    if not items:
        for match in item_pattern2.finditer(raw_text):
            product_name = match.group(1).strip()
            if product_name.lower() in seen_products:
                continue
            skip_words = ['product', 'description', 'item', 'subtotal', 'total', 'no.', 'qty', 'price']
            if any(w in product_name.lower() for w in skip_words):
                continue
            try:
                qty = int(match.group(2))
                unit_price = float(match.group(3).replace(',', ''))
                total_price = float(match.group(4).replace(',', ''))
                if qty <= 0 or unit_price <= 0:
                    continue

                confidence = calculate_confidence(product_name, qty, unit_price, total_price)
                items.append({
                    "product_name": product_name,
                    "unit": "pcs",
                    "quantity": qty,
                    "unit_price": round(unit_price, 2),
                    "total_price": round(total_price, 2),
                    "confidence": confidence
                })
                seen_products.add(product_name.lower())
            except (ValueError, AttributeError):
                continue

    return items


def calculate_confidence(product_name: str, qty: int, unit_price: float, total_price: float) -> float:
    """Calculate OCR confidence score 0-100 based on data consistency"""
    score = 60.0  # base score

    # Math check: qty * unit_price should approximately equal total
    expected = qty * unit_price
    if total_price > 0:
        ratio = expected / total_price
        if 0.95 <= ratio <= 1.05:
            score += 25.0  # Math checks out perfectly
        elif 0.85 <= ratio <= 1.15:
            score += 10.0  # Close enough
        else:
            score -= 15.0  # Math doesn't add up

    # Product name quality check
    if len(product_name) >= 5:
        score += 5.0
    if any(char.isdigit() for char in product_name):
        score += 5.0  # Has size/quantity in name (e.g. "330ml")
    if len(product_name) > 50:
        score -= 10.0  # Too long, likely OCR garbage

    return min(100.0, max(0.0, round(score, 1)))


def process_invoice_file(file_path: str, file_type: str) -> dict:
    """
    Main entry point: process invoice file and return structured data.
    file_type: 'IMAGE' or 'PDF'
    """
    # Resolve absolute path - try multiple locations
    abs_path = None
    candidates = [
        file_path,
        os.path.join(os.getcwd(), file_path),
        os.path.join(os.path.dirname(os.getcwd()), file_path),
        # Spring Boot uploads relative to project
        os.path.join(os.getcwd(), '..', 'spring-boot-backend', file_path),
    ]
    for candidate in candidates:
        if os.path.exists(candidate):
            abs_path = candidate
            break

    if not abs_path:
        raise FileNotFoundError(
            f"Invoice file not found: {file_path}\n"
            f"Tried: {candidates}\n"
            f"Current dir: {os.getcwd()}"
        )

    # Extract text
    if file_type.upper() == 'PDF':
        raw_text = extract_text_from_pdf(abs_path)
    else:
        raw_text = extract_text_from_image(abs_path)

    if not raw_text or len(raw_text) < 20:
        raise ValueError("Could not extract meaningful text from the invoice file. "
                         "Ensure the image is clear and well-lit.")

    # Parse structured data
    parsed = parse_invoice_data(raw_text)
    parsed["file_path"] = abs_path
    parsed["file_type"] = file_type
    parsed["status"] = "success"
    parsed["items_count"] = len(parsed["items"])

    logger.info(f"OCR processed: {abs_path} -> {len(parsed['items'])} items extracted")
    return parsed
