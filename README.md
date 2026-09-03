

<p align="center"><img alt="Screening dashboard" border="0" src="https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEjEB58d-ULW-o-lYb86KZSTMumx5EL1iHvpZzLCQBEDOBqF5oHRN3ZlcrP3I5Eai2Pq1Z8HENoY_Q_gszLj7_ItCjg9Q2ya_flJejHGglE79kuwuUl-6tb7C3MSBzOvMCjnCrDfjtV9tO1i8MUJWHJMEc55vV23XVyNrkdaKkqL-PKEAgYDbKk8HWcCxxo/s1536/4da056d1-8e0f-4ccb-ba86-6dca1471faec.png"/></p>


## AI-Powered Inventory Management & Demand Forecasting with OCR

---

## System Requirements

| Component | Requirement |
|-----------|------------|
| Java | 21 (JDK) |
| Maven | 3.8+ |
| MySQL | 8.0+ (via XAMPP) |
| Python | 3.10+ |
| IDE | IntelliJ IDEA / Eclipse |
| Tesseract | 4.0+ (for real OCR) |

---

## Quick Start (Step by Step)

### STEP 1: Database Setup

1. Start **XAMPP** → Start **MySQL**
2. Open **phpMyAdmin** → `http://localhost/phpmyadmin`
3. Open SQL tab and run: `database/schema/01_schema.sql`
4. This creates all tables + demo data automatically

---

### STEP 2: Spring Boot Backend

1. Open `spring-boot-backend` folder in **IntelliJ IDEA** or **Eclipse**
2. Wait for Maven to download dependencies
3. Edit `src/main/resources/application.properties` if needed:
   ```properties
   spring.datasource.username=root
   spring.datasource.password=        # your MySQL password (blank for XAMPP default)
   ```
4. Run `StockSenseApplication.java`
5. App starts at: **http://localhost:8080**

---

### STEP 3: FastAPI AI Service

The FastAPI AI service now starts automatically with Spring Boot. On the first
macOS/Linux launch it creates `fastapi-service/venv`, installs the Python
packages, and then starts OCR and forecasting. The first launch can take a few
minutes. Use the commands below only if you want to run the service manually.

**Option A - Windows:**
```
cd fastapi-service
start.bat
```

**Option B - Linux/Mac:**
```bash
cd fastapi-service
chmod +x start.sh
./start.sh
```

**Option C - Manual:**
```bash
cd fastapi-service
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

AI Service runs at: **http://localhost:8000**
API Docs: **http://localhost:8000/docs**

---

### STEP 4: Install Tesseract OCR (For Real OCR)

**Windows:**
- Download installer: https://github.com/UB-Mannheim/tesseract/wiki
- Install to default path
- Add to PATH environment variable

**Ubuntu/Debian:**
```bash
sudo apt-get install tesseract-ocr
```

**Mac:**
```bash
brew install tesseract
```

> **Note:** Without Tesseract, the OCR service runs in **Demo Mode** which generates realistic sample data for testing.

---

## Default Login Credentials

| Role | Username | Password | Access |
|------|----------|----------|--------|
| **Admin** | `admin` | `admin123` | Full system access |
| **Inventory Manager** | `manager` | `admin123` | Products, inventory, OCR, forecasting |
| **Staff** | `staff1` | `admin123` | Sales (POS) only |

---

## System Architecture

```
Browser
  ↓ HTTP
Spring Boot (Port 8080)
  ├── Thymeleaf Templates (UI)
  ├── REST APIs
  ├── Spring Security (Auth)
  └── JPA/Hibernate → MySQL (Port 3306)
        ↓ REST (when AI features used)
      FastAPI (Port 8000)
        ├── AI Forecasting (scikit-learn RandomForest)
        └── OCR Processing (Tesseract/pdfplumber)
```

---

## Role-Based Access Control

### Admin (ROLE_ADMIN)
- Full system access
- User management
- Audit logs
- All inventory features
- All reports

### Inventory Manager (ROLE_INVENTORY_MANAGER)
- Product CRUD
- Category management
- Supplier management
- Inventory logs & adjustments
- OCR invoice processing
- AI demand forecasting
- Reports
- **Cannot:** manage users, view audit logs

### Staff (ROLE_STAFF)
- New Sale (POS terminal)
- Sales history view
- Print receipts
- **Cannot:** manage products, suppliers, inventory, OCR, forecasting, reports, users

---

## Project Structure

```
smart-inventory/
├── spring-boot-backend/          # Java Spring Boot app
│   ├── src/main/java/com/stocksense/
│   │   ├── StockSenseApplication.java
│   │   ├── config/               # App configuration
│   │   ├── controller/           # MVC Controllers
│   │   ├── dto/                  # Data Transfer Objects
│   │   │   ├── request/          # Request DTOs
│   │   │   └── response/         # Response DTOs
│   │   ├── entity/               # JPA Entities
│   │   ├── exception/            # Global exception handling
│   │   ├── repository/           # Spring Data repositories
│   │   ├── security/             # Spring Security config
│   │   └── service/              # Business logic
│   └── src/main/resources/
│       ├── application.properties
│       └── templates/            # Thymeleaf HTML templates
│           ├── auth/             # Login, profile
│           ├── dashboard/        # Dashboard
│           ├── products/         # Product management
│           ├── suppliers/        # Supplier management
│           ├── inventory/        # Inventory logs
│           ├── sales/            # POS & sales history
│           ├── ocr/              # OCR invoice processing
│           ├── forecasting/      # AI forecasting
│           ├── reports/          # Reports
│           ├── admin/            # User management, audit
│           └── fragments/        # Shared layout
│
├── fastapi-service/              # Python AI/OCR service
│   ├── main.py                   # FastAPI app entry
│   ├── requirements.txt          # Python dependencies
│   ├── start.sh / start.bat      # Startup scripts
│   ├── routers/
│   │   ├── forecast_router.py    # Forecasting endpoints
│   │   └── ocr_router.py         # OCR endpoints
│   ├── services/
│   │   ├── forecast_service.py   # ML forecasting logic
│   │   └── ocr_service.py        # OCR processing logic
│   ├── schemas/                  # Pydantic models
│   └── ml_models/                # Saved ML models (auto-created)
│
└── database/
    └── schema/
        └── 01_schema.sql         # Full DB schema + seed data
```

---

## Key Features

### AI Demand Forecasting
- Random Forest ML model
- Historical sales analysis
- Confidence intervals
- 7-90 day predictions
- Auto-retrains on demand
- Fallback to moving average

### OCR Invoice Processing
- Upload JPG/PNG/PDF invoices
- Automatic text extraction
- Smart product/price detection
- Manual validation workflow
- One-click inventory update

### POS Sales System
- Real-time product search
- Cart management
- Auto stock deduction
- Receipt generation
- Multiple payment methods

### Inventory Management
- Stock IN/OUT tracking
- Low stock alerts
- Full audit trail
- Adjustment history

### Role-Based Security
- 3 distinct roles
- Spring Security integration
- Session management
- Audit logging

---

## Configuration

### Change Database Password
Edit `spring-boot-backend/src/main/resources/application.properties`:
```properties
spring.datasource.password=your_password_here
```

### Change AI Service URL
```properties
app.ai-service.base-url=http://localhost:8000
```

### Upload Directory
```properties
app.upload.dir=uploads
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Port 8080 in use | Change `server.port` in application.properties |
| DB connection failed | Check MySQL is running, verify credentials |
| OCR shows demo data | Install Tesseract OCR (see Step 4) |
| AI service offline | Forecasting uses fallback moving average |
| Products image not showing | Check `uploads/products` directory exists |

---

## API Endpoints (FastAPI)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/forecast/predict` | POST | Generate demand forecast |
| `/api/forecast/retrain` | POST | Retrain AI model |
| `/api/forecast/status` | GET | Model status |
| `/api/ocr/process` | POST | Process invoice OCR |
| `/api/ocr/status` | GET | OCR service status |
| `/docs` | GET | Interactive API docs |

---

## Final Year Project Notes

This system demonstrates:
- **Enterprise Architecture:** Layered design, DTO pattern, repository pattern

### Testing

The project includes unit tests for the Spring Boot services and POS controller,
as well as the FastAPI OCR and forecasting services. The test inventory and
manual acceptance scenarios are documented in `TEST_CASES.md`.

Run the automated tests with:

```bash
# Spring Boot backend
cd spring-boot-backend
mvn test

# FastAPI service
cd ../fastapi-service
python -m pytest tests/ -v
```

The POS test coverage verifies that `/sales/create` loads active products into
the POS view, returns a flat success response for receipt printing, and safely
reports insufficient-stock errors.
- **AI/ML Integration:** scikit-learn RandomForest for demand forecasting
- **OCR Technology:** Tesseract + pdfplumber for document processing
- **Microservices:** Spring Boot + FastAPI integration
- **Security:** Spring Security with role-based access
- **Database Design:** Normalized schema with proper indexing

---

## Author

**Sameera Chathuranga**  
BSc (Hons) Software Engineering  
ICBT Campus | Cardiff Metropolitan University

---

## License

Academic project — intended for educational and evaluation purposes

---

*StockSense v1.1 | Built with Spring Boot 3 + FastAPI + MySQL*