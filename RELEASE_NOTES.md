# StockSense v1.0.1 — Release Notes

## Overview
StockSense is an AI-powered inventory management system combining a Spring Boot
backend with a FastAPI microservice for OCR invoice processing and demand
forecasting.

## Highlights
- Role-based inventory, sales (POS), and supplier management
- OCR invoice processing with automatic line-item extraction
- AI-driven demand forecasting per product (RandomForest + statistical fallback)
- Real-time dashboard with revenue, sales, and stock analytics
- Cross-platform auto-start integration between Spring Boot and the FastAPI
  AI service
- Audit logging and role-based access control (Admin / Inventory Manager / Staff)

## Stack
- Backend: Spring Boot 3.2, Java 21, Spring Security, Spring Data JPA, MySQL
- AI Service: FastAPI, scikit-learn, pytesseract, pdfplumber
- Frontend: Thymeleaf, Bootstrap 5

## Status
Version 1.0.0 — feature-complete for the core inventory, sales, OCR, and
forecasting workflows.
