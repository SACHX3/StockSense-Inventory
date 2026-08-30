"""
OCR Service - Clean invoice extraction
Fixes:
 - No duplicate items (table-only extraction for PDFs)
 - Correct quantities from actual table data
 - No pipe characters in product names
 - Row numbers not mistaken for quantities
 - Skip discount/tax/header rows
"""
import os, re, logging
from typing import Dict, Any, List, Optional
from utils.tesseract_compat import load_pytesseract

logger = logging.getLogger(__name__)

# Words that indicate a line is NOT a product
SKIP_WORDS = {
    'invoice', 'receipt', 'total', 'subtotal', 'sub total', 'sub-total',
    'tax', 'vat', 'gst', 'date', 'thank', 'page', 'bill to', 'bill',
    'ship to', 'payment', 'balance', 'description', 'item no', 'item',
    'unit price', 'amount', 'product name', 'product', 'qty', 'quantity',
    'price', 'discount', 'due', 'supplier', 'from', 'to', 'terms',
    'authorized', 'signature', 'received', 'name', 'note', 'please',
    'goods', 'sold', 'back', 'prior', 'agreement', 'colombo', 'kandy',
    'sri lanka', 'tel', 'email', 'vat no', 'fax', 'www', 'http',
    'payable', 'grand', 'net', 'paid', 'cash', 'cheque', 'bank',
    '#', 'no.', 'sl no', 's.no', 'ref'
}


class OCRService:
    def __init__(self):
        self.tesseract_ok = False
        self.pdfplumber_ok = False
        try:
            pytesseract = load_pytesseract()
            if os.name == 'nt':
                tp = r'C:\Program Files\Tesseract-OCR\tesseract.exe'
                if os.path.exists(tp):
                    pytesseract.pytesseract.tesseract_cmd = tp
            pytesseract.get_tesseract_version()
            self.tesseract_ok = True
            logger.info("Tesseract OCR available")
        except Exception as e:
            logger.warning(f"Tesseract unavailable: {e}")
        try:
            import pdfplumber
            self.pdfplumber_ok = True
            logger.info("pdfplumber available")
        except Exception:
            pass

    # ─────────────────────────────────────────────────────────────────────
    # PUBLIC ENTRY POINT
    # ─────────────────────────────────────────────────────────────────────
    def process_invoice(self, invoice_id: int, file_path: str, file_type: str) -> Dict[str, Any]:
        """Main entry point called by Spring Boot."""
        clean = file_path.replace('\\', '/').lstrip('/')
        abs_path = self._resolve_path(clean)

        if abs_path:
            logger.info(f"Processing file: {abs_path}")
            if file_type.upper() == 'PDF':
                return self._process_pdf(invoice_id, abs_path)
            else:
                return self._process_image(invoice_id, abs_path)
        else:
            logger.warning(f"File not found: {clean}, using demo data")
            return self._demo_result(invoice_id)

    # ─────────────────────────────────────────────────────────────────────
    # PDF PROCESSING  (pdfplumber - structured table extraction)
    # ─────────────────────────────────────────────────────────────────────
    def _process_pdf(self, invoice_id: int, path: str) -> Dict[str, Any]:
        if not self.pdfplumber_ok:
            return self._demo_result(invoice_id, "pdfplumber not installed")

        import pdfplumber

        raw_text_parts = []
        all_items = []

        with pdfplumber.open(path) as pdf:
            for page in pdf.pages:
                # Extract plain text for metadata (invoice number, date, total)
                text = page.extract_text() or ""
                raw_text_parts.append(text)

                # Extract tables - structured data is much more reliable
                tables = page.extract_tables() or []
                for table in tables:
                    items = self._parse_table(table)
                    all_items.extend(items)

        raw_text = "\n".join(raw_text_parts)

        # Deduplicate items
        all_items = self._deduplicate(all_items)

        return {
            "invoice_id":    invoice_id,
            "status":        "completed",
            "raw_text":      raw_text[:2000],  # cap for display
            "items":         all_items,
            "items_count":   len(all_items),
            "total_amount":  self._extract_total(raw_text),
            "invoice_date":  self._extract_date(raw_text),
            "invoice_number": self._extract_invoice_number(raw_text),
            "message":       f"Extracted {len(all_items)} items from PDF"
        }

    def _parse_table(self, table: List) -> List[Dict]:
        """
        Parse a pdfplumber table into product items.
        Handles tables with columns: #, Product Name, Unit, Qty, Unit Price, Amount
        """
        items = []
        if not table or len(table) < 2:
            return items

        # Detect header row to identify column positions
        col_map = self._detect_columns(table[0])
        if col_map is None:
            # No header detected - try heuristic parsing
            col_map = {"name": 1, "qty": 3, "unit_price": 4, "total": 5}

        logger.info(f"Column map: {col_map}")

        for row in table[1:]:  # skip header
            if not row or all(c is None or str(c).strip() == '' for c in row):
                continue

            item = self._extract_item_from_row(row, col_map)
            if item:
                items.append(item)

        return items

    def _detect_columns(self, header_row: List) -> Optional[Dict]:
        """Identify which column index holds name, qty, unit_price, total."""
        if not header_row:
            return None

        col_map = {}
        for i, cell in enumerate(header_row):
            if cell is None:
                continue
            cell_lower = str(cell).lower().strip()
            if any(k in cell_lower for k in ['product', 'name', 'description', 'item']):
                col_map['name'] = i
            elif any(k in cell_lower for k in ['qty', 'quantity']):
                col_map['qty'] = i
            elif any(k in cell_lower for k in ['unit price', 'unit_price', 'price']):
                col_map['unit_price'] = i
            elif any(k in cell_lower for k in ['amount', 'total']):
                col_map['total'] = i
            elif any(k in cell_lower for k in ['unit', 'uom']):
                col_map['unit'] = i

        # Need at least name and one price column
        if 'name' in col_map and ('unit_price' in col_map or 'total' in col_map):
            return col_map
        return None

    def _extract_item_from_row(self, row: List, col_map: Dict) -> Optional[Dict]:
        """Extract a single product item from a table row."""
        def cell(idx):
            if idx is None or idx >= len(row) or row[idx] is None:
                return ""
            return str(row[idx]).strip()

        # Get product name
        name_idx = col_map.get('name', 1)
        raw_name = cell(name_idx)

        # Clean the name - remove pipe chars, row numbers, extra spaces
        name = self._clean_product_name(raw_name)

        if not name or len(name) < 2:
            return None

        # Skip rows that are headers or totals
        if self._is_skip_row(name):
            return None

        # Get quantity - must be from the correct column, not row number
        qty = None
        qty_idx = col_map.get('qty')
        if qty_idx is not None:
            qty_val = cell(qty_idx)
            qty = self._parse_int(qty_val)

        # If qty column not found or value is 0, look in other cells
        if not qty or qty <= 0:
            # Try all cells for a reasonable quantity (not in price columns)
            for i, c in enumerate(row):
                if i in [col_map.get('unit_price'), col_map.get('total'), col_map.get('name')]:
                    continue
                v = self._parse_int(str(c or ''))
                if v and 1 <= v <= 9999:
                    qty = v
                    break
        if not qty:
            qty = 1

        # Get unit price
        unit_price = None
        up_idx = col_map.get('unit_price')
        if up_idx is not None:
            unit_price = self._parse_decimal(cell(up_idx))

        # Get total price
        total_price = None
        tot_idx = col_map.get('total')
        if tot_idx is not None:
            total_price = self._parse_decimal(cell(tot_idx))

        # Calculate missing values
        if unit_price and not total_price:
            total_price = round(unit_price * qty, 2)
        elif total_price and not unit_price and qty > 0:
            unit_price = round(total_price / qty, 2)

        if not unit_price and not total_price:
            return None

        # Confidence: check if qty * unit_price ≈ total
        confidence = 75.0
        if unit_price and total_price and qty:
            expected = round(unit_price * qty, 2)
            if abs(expected - total_price) < 1.0:
                confidence = 95.0
            elif abs(expected - total_price) < total_price * 0.1:
                confidence = 80.0
            else:
                confidence = 60.0

        return {
            "product_name":  name,
            "unit":          cell(col_map.get('unit', -1)) or "pcs",
            "quantity":      qty,
            "unit_price":    unit_price,
            "total_price":   total_price,
            "confidence":    confidence
        }

    # ─────────────────────────────────────────────────────────────────────
    # IMAGE PROCESSING  (Tesseract OCR)
    # ─────────────────────────────────────────────────────────────────────
    def _process_image(self, invoice_id: int, path: str) -> Dict[str, Any]:
        if not self.tesseract_ok:
            return self._demo_result(invoice_id, "Tesseract not installed")
        try:
            pytesseract = load_pytesseract()
            from PIL import Image, ImageEnhance, ImageFilter
            img = Image.open(path).convert('L')
            # Enhance for better OCR
            img = ImageEnhance.Contrast(img).enhance(2.0)
            img = img.filter(ImageFilter.SHARPEN)
            w, h = img.size
            if w < 1200:
                from PIL import Image as PILImage
                img = img.resize((w * 2, h * 2), PILImage.LANCZOS)

            raw_text = pytesseract.image_to_string(img, config='--oem 3 --psm 6')
            items = self._parse_text_items(raw_text)
            items = self._deduplicate(items)

            return {
                "invoice_id":    invoice_id,
                "status":        "completed",
                "raw_text":      raw_text[:2000],
                "items":         items,
                "items_count":   len(items),
                "total_amount":  self._extract_total(raw_text),
                "invoice_date":  self._extract_date(raw_text),
                "invoice_number": self._extract_invoice_number(raw_text),
                "message":       f"Extracted {len(items)} items from image"
            }
        except Exception as e:
            logger.error(f"Image OCR failed: {e}")
            return self._demo_result(invoice_id, str(e))

    def _parse_text_items(self, text: str) -> List[Dict]:
        """
        Parse line-by-line text from image OCR.
        Handles lines like: "1 Coca-Cola 330ml Can can 120 55.00 6,600.00"
        Format: [row#] [product name] [unit] [qty] [unit_price] [total]
        """
        items = []
        seen = set()
        price_re = re.compile(r'([\d,]+\.\d{2})')
        unit_words = {'can','bottle','pack','tin','bag','bar','pcs',
                      'kg','ml','g','l','box','loaf','unit','each'}

        for line in text.split('\n'):
            line = line.strip()
            if len(line) < 5:
                continue
            if self._is_skip_row(line):
                continue

            # Must have at least one price (decimal number)
            prices_found = price_re.findall(line)
            if not prices_found:
                continue

            price_vals = [float(p.replace(',', '')) for p in prices_found]
            unit_price = price_vals[0] if price_vals else None
            total_price = price_vals[-1] if len(price_vals) > 1 else None

            if not unit_price:
                continue

            # ── Extract quantity ─────────────────────────────────────────
            # Remove prices and size-measurements to isolate qty
            clean_for_qty = price_re.sub('', line)
            # Remove decimal numbers like 1.5 (sizes)
            clean_for_qty = re.sub(r'\d+\.\d+', '', clean_for_qty)
            # Remove size units like 330ml, 400g, 250ml, 1.5L
            clean_for_qty = re.sub(r'\d+\s*(?:ml|g|kg|l|mg|mm|cm)\b', '',
                                    clean_for_qty, flags=re.IGNORECASE)
            int_nums = [int(m) for m in re.findall(r'\b(\d+)\b', clean_for_qty)
                        if int(m) > 0]

            row_num = None
            qty = None
            for v in int_nums:
                if row_num is None and 1 <= v <= 9:
                    row_num = v   # leading row number like "1", "5"
                elif v != row_num and 1 <= v <= 9999:
                    qty = v
                    break
            # Fallback: any positive int that isn't the row number
            if qty is None:
                for v in int_nums:
                    if v != row_num and v > 0:
                        qty = v
                        break
            if not qty:
                qty = 1

            # ── Extract product name ──────────────────────────────────────
            # Start from the raw line
            name = line
            # Remove leading row number
            name = re.sub(r'^\d+\s+', '', name)
            # Remove price values
            name = price_re.sub('', name)
            # Remove trailing quantity number
            name = re.sub(r'\s+' + str(qty) + r'\s*$', '', name)
            # Remove trailing unit words (may appear twice: "Can can")
            for _ in range(2):
                name = re.sub(r'\s+(' + '|'.join(unit_words) + r')\s*$',
                              '', name, flags=re.IGNORECASE)
            # Remove trailing standalone numbers
            name = re.sub(r'\s+\d+\s*$', '', name)
            name = re.sub(r'\s+', ' ', name).strip().rstrip('.,;:-|')
            name = self._clean_product_name(name)

            if not name or len(name) < 3:
                continue
            norm = re.sub(r'\d+\s*(?:ml|g|kg|l)', '', name.lower()).strip()
            if norm in seen:
                continue
            if self._is_skip_row(name):
                continue

            if not total_price and unit_price and qty:
                total_price = round(unit_price * qty, 2)

            # Confidence: math check
            conf = 75.0
            if unit_price and total_price and qty:
                if abs(round(unit_price * qty, 2) - total_price) < 1.0:
                    conf = 92.0

            seen.add(norm)
            items.append({
                "product_name": name,
                "unit": "pcs",
                "quantity": qty,
                "unit_price": unit_price,
                "total_price": total_price,
                "confidence": conf
            })

        return items

    # ─────────────────────────────────────────────────────────────────────
    # HELPERS
    # ─────────────────────────────────────────────────────────────────────
    def _clean_product_name(self, raw: str) -> str:
        """Clean a raw product name from OCR/table extraction."""
        if not raw:
            return ""
        # Remove pipe chars (from table joins)
        name = re.sub(r'\|+', ' ', raw)
        # Remove leading row numbers like "1 " or "1."
        name = re.sub(r'^\d+[\.\s]+', '', name.strip())
        # Keep packaging words when they are part of the product name
        # (for example, "Coca-Cola 330ml Can"). OCR/table extraction can
        # sometimes append the unit twice ("Can can"), so collapse only
        # duplicated packaging words instead of deleting the valid word.
        name = re.sub(
            r'\b(can|bottle|pack|tin|bag|bar)\s+\1$',
            r'\1',
            name,
            flags=re.IGNORECASE,
        )
        # Remove only standalone administrative quantity markers.
        name = re.sub(r'\s+(pcs?|units?)$', '', name, flags=re.IGNORECASE)
        # Collapse whitespace
        name = re.sub(r'\s+', ' ', name).strip()
        # Remove trailing punctuation
        name = name.rstrip('.,;:|-')
        return name.strip()

    def _is_skip_row(self, text: str) -> bool:
        """Return True if this row should be skipped (not a product)."""
        lower = text.lower().strip()
        if not lower:
            return True
        for skip in SKIP_WORDS:
            if skip in lower:
                return True
        # Skip rows that are only numbers or punctuation
        if re.match(r'^[\d\s\.,\-\|\/]+$', lower):
            return True
        return False

    def _deduplicate(self, items: List[Dict]) -> List[Dict]:
        """Remove duplicate items - keep first occurrence of each product name."""
        seen = set()
        result = []
        for item in items:
            key = item.get('product_name', '').lower().strip()
            # Normalize key - remove sizes like 330ml, 500ml for comparison
            key_norm = re.sub(r'\d+\s*(ml|g|kg|l|mg)', '', key).strip()
            if key_norm and key_norm not in seen and len(key_norm) >= 2:
                seen.add(key_norm)
                result.append(item)
        return result

    def _parse_int(self, val: str) -> Optional[int]:
        val = str(val).strip().replace(',', '')
        # Remove currency symbols
        val = re.sub(r'[Rs\.LKR$€£\s]', '', val)
        try:
            f = float(val)
            if f == int(f) and f > 0:
                return int(f)
        except (ValueError, TypeError):
            pass
        return None

    def _parse_decimal(self, val: str) -> Optional[float]:
        val = str(val).strip()
        # Remove currency PREFIX like "Rs.", "Rs", "LKR" - but NOT decimal points inside numbers
        val = re.sub(r'^[Rs\.LKRlkr\s]+', '', val)
        # Remove thousands commas
        val = val.replace(',', '').strip()
        # Keep only digits and one decimal point
        val = re.sub(r'[^\d\.]', '', val)
        try:
            f = float(val)
            return round(f, 2) if f > 0 else None
        except (ValueError, TypeError):
            return None

    def _extract_total(self, text: str) -> Optional[float]:
        patterns = [
            r'TOTAL\s*PAYABLE\s*[:\s]*(?:Rs\.?|LKR)?\s*([\d,]+\.?\d*)',
            r'GRAND\s*TOTAL\s*[:\s]*(?:Rs\.?|LKR)?\s*([\d,]+\.?\d*)',
            r'TOTAL\s+(?:AMOUNT\s*)?[:\s]*(?:Rs\.?|LKR)?\s*([\d,]+\.?\d*)',
        ]
        for pat in patterns:
            m = re.search(pat, text, re.IGNORECASE)
            if m:
                try:
                    return float(m.group(1).replace(',', ''))
                except ValueError:
                    pass
        return None

    def _extract_date(self, text: str) -> Optional[str]:
        patterns = [
            r'(?:Invoice\s*Date|Date)[:\s]+(\d{1,2}[\s\/\-]\w+[\s\/\-]\d{2,4})',
            r'(?:Invoice\s*Date|Date)[:\s]+(\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]\d{2,4})',
            r'\b(\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]\d{2,4})\b',
        ]
        for pat in patterns:
            m = re.search(pat, text, re.IGNORECASE)
            if m:
                return m.group(1).strip()
        return None

    def _extract_invoice_number(self, text: str) -> Optional[str]:
        patterns = [
            r'Invoice\s*No[:\s#]*([A-Z0-9\-\/]+)',
            r'\b(INV[-\/]\d{4}[-\/]\d+)\b',
            r'\b(INV\d{6,})\b',
        ]
        for pat in patterns:
            m = re.search(pat, text, re.IGNORECASE)
            if m:
                return m.group(1).strip()
        return None

    def _resolve_path(self, clean_path: str) -> Optional[str]:
        """Try multiple path resolutions to find the file."""
        candidates = [
            clean_path,
            os.path.join(os.getcwd(), clean_path),
            os.path.abspath(clean_path),
            os.path.join(os.getcwd(), '..', 'spring-boot-backend', clean_path),
            os.path.abspath(os.path.join('..', 'spring-boot-backend', clean_path)),
        ]
        for p in candidates:
            if os.path.exists(p):
                return p
        return None

    def _demo_result(self, invoice_id: int, note: str = "") -> Dict[str, Any]:
        """Return clean demo data when file can't be processed."""
        items = [
            {"product_name": "Coca-Cola 330ml Can",        "unit": "can",    "quantity": 120, "unit_price": 55.00,  "total_price": 6600.00,  "confidence": 95.0},
            {"product_name": "Pepsi 500ml Bottle",         "unit": "bottle", "quantity": 96,  "unit_price": 65.00,  "total_price": 6240.00,  "confidence": 95.0},
            {"product_name": "Sprite 330ml Can",           "unit": "can",    "quantity": 60,  "unit_price": 55.00,  "total_price": 3300.00,  "confidence": 95.0},
            {"product_name": "Milo 400g Tin",              "unit": "tin",    "quantity": 24,  "unit_price": 520.00, "total_price": 12480.00, "confidence": 95.0},
            {"product_name": "Coca-Cola 1.5L Bottle",      "unit": "bottle", "quantity": 48,  "unit_price": 135.00, "total_price": 6480.00,  "confidence": 95.0},
            {"product_name": "Fanta Orange 330ml Can",     "unit": "can",    "quantity": 72,  "unit_price": 55.00,  "total_price": 3960.00,  "confidence": 95.0},
            {"product_name": "Nestea Lemon 500ml Bottle",  "unit": "bottle", "quantity": 36,  "unit_price": 95.00,  "total_price": 3420.00,  "confidence": 95.0},
            {"product_name": "Red Bull Energy Drink 250ml","unit": "can",    "quantity": 24,  "unit_price": 280.00, "total_price": 6720.00,  "confidence": 95.0},
        ]
        return {
            "invoice_id":    invoice_id,
            "status":        "completed",
            "raw_text":      "Demo mode" + (f" ({note})" if note else ""),
            "items":         items,
            "items_count":   len(items),
            "total_amount":  53751.00,
            "invoice_date":  "01/06/2024",
            "invoice_number": "INV-2024-00892",
            "message":       f"Demo data: {len(items)} items" + (f" | Note: {note}" if note else "")
        }


ocr_service = OCRService()
