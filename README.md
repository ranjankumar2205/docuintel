# DocuIntel — AI-Powered Document Intelligence Platform

> Upload any PDF. Get instant classification, structured data extraction, and a document-aware chat — all powered by Gemini 2.5 Flash and a full RAG pipeline.

---

## What Is DocuIntel?

DocuIntel is a full-stack AI application that turns unstructured PDF documents into structured, queryable data. Drop in an invoice, resume, bank statement, payslip, offer letter, or receipt — the system identifies what it is, extracts the right fields for that document type, and opens a chat interface so you can ask natural-language questions about the document's content.

No templates. No rules. Pure LLM intelligence grounded in the actual document.

---

## Live Demo Flow

```
Upload PDF  →  Classification  →  Field Extraction  →  Document Chat
```

1. **Upload** — drag-and-drop or browse a PDF (up to 50 MB)
2. **Classify** — Gemini identifies document type and produces a title, summary, and confidence score
3. **Extract** — type-specific fields are pulled out (e.g. invoice number, vendor, total; or candidate name, position, salary)
4. **Chat** — ask anything about the document; answers are grounded exclusively in the uploaded content
5. **Export** — download extracted fields as CSV, or process dozens of files at once in Bulk Mode

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Frontend** | Angular 21 · Tailwind CSS v4 · Angular SSR · TypeScript 5.9 |
| **Backend** | Spring Boot 4.0 · Java 21 · Spring AI 2.0 |
| **AI Model** | Google Gemini 2.5 Flash (chat) · `gemini-embedding-001` (embeddings) |
| **Vector Store** | Spring AI `SimpleVectorStore` (in-memory; swappable) |
| **PDF Parsing** | Apache PDFBox 3.0 |
| **Build** | Gradle 9 · npm 11 |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Angular 21 Frontend                   │
│   Upload · Classification Panel · Extraction Table · Chat    │
│                  Bulk Mode · CSV Export                      │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTP (REST)
                             ▼
┌─────────────────────────────────────────────────────────────┐
│               Spring Boot 4.0 Backend  (:8000)              │
│                                                             │
│  DocumentController                                         │
│    POST /upload-pdf     POST /api/v1/extract    POST /chat  │
│         │                      │                    │       │
│         ▼                      ▼                    ▼       │
│  PdfExtractionService   ExtractionService    VectorStore    │
│  TextChunkingService    ClassificationService  Service      │
│         │                      │                    │       │
│         └──────────────────────┴────────────────────┘       │
│                               │                             │
│                    Spring AI ChatClient                      │
└───────────────────────────────┬─────────────────────────────┘
                                │
               ┌────────────────┴─────────────────┐
               │                                  │
               ▼                                  ▼
   Google Gemini 2.5 Flash             gemini-embedding-001
   (classification · extraction        (chunk embeddings ·
    · chat completions)                 semantic search)
```

### RAG Pipeline (Retrieval-Augmented Generation)

```
PDF Upload
    │
    ▼
PdfExtractionService       — PDFBox text extraction + deduplication
    │
    ▼
TextChunkingService        — sliding word-window chunks (500 words, 100 overlap)
    │
    ▼
VectorStoreService.add()   — embed each chunk → store (text + vector + metadata)
    │
    ▼
  [session_id assigned]

                  ─── later, on /extract or /chat ───

Query arrives
    │
    ▼
VectorStoreService.search()  — embed query → top-5 cosine similarity
    │  (filtered by session_id so sessions never bleed into each other)
    ▼
buildContext()               — join retrieved chunks
    │
    ▼
Gemini 2.5 Flash             — answer grounded in document context only
```

---

## Project Structure

```
DocuIntel/
├── backend/                         # Spring Boot application
│   └── src/main/java/org/example/docuintel/
│       ├── DocuIntelApplication.java
│       ├── config/
│       │   ├── AppConfig.java       # Property binding + upload dir init
│       │   ├── VectorStoreConfig.java  # SimpleVectorStore bean
│       │   └── WebConfig.java       # CORS configuration
│       ├── controller/
│       │   ├── DocumentController.java       # REST endpoints
│       │   └── GlobalExceptionHandler.java   # Centralised error handling
│       ├── model/
│       │   └── ApiModels.java       # Request/response records
│       └── service/
│           ├── ClassificationService.java   # Gemini document classification
│           ├── ExtractionService.java       # Gemini field extraction
│           ├── PdfExtractionService.java    # PDFBox text extraction
│           ├── TextChunkingService.java     # Overlapping chunk splitting
│           └── VectorStoreService.java      # RAG ingestion + retrieval + chat
│
└── frontend/                        # Angular 21 application
    └── src/app/
        ├── app.ts                   # Single-component app (signals-based state)
        ├── app.html                 # Template (upload, tabs, chat UI)
        ├── app.css                  # Component styles
        └── styles.css               # Global Tailwind styles
```

---

## API Reference

### `GET /`
Health check.
```json
{ "message": "Backend Running Successfully", "status": "UP" }
```

### `POST /upload-pdf`
Upload a PDF. Returns a session ID and classification.

**Request:** `multipart/form-data`, field name `file`

**Response:**
```json
{
  "session_id": "f40e847c-3769-4b8f-b42a-acb988b196df",
  "classification_result": {
    "document_type": "Invoice",
    "document_title": "Acme Corp Q1 Invoice",
    "document_summary": "Invoice for consulting services rendered in Q1 2024.",
    "confidence_score": "0.97"
  }
}
```

### `POST /api/v1/extract`
Extract structured fields for a previously uploaded document.

**Request:**
```json
{ "session_id": "f40e847c-..." }
```

**Response (Invoice example):**
```json
{
  "extracted_data": {
    "document_type": "Invoice",
    "invoice_number": "INV-2024-001",
    "vendor_name": "Acme Corp",
    "invoice_date": "2024-01-15",
    "total_amount": "4500.00",
    "currency": "USD"
  }
}
```

**Supported document types and their fields:**

| Type | Extracted Fields |
|---|---|
| Invoice | invoice_number, vendor_name, invoice_date, total_amount, currency |
| Resume | name, email, phone, skills, experience |
| Receipt | merchant_name, transaction_date, total_amount, payment_method, currency |
| Payslip | employee_name, employee_id, gross_pay, net_pay, month, designation |
| Bank Statement | bank_name, account_holder, opening_balance, closing_balance, statement_period |
| Offer Letter | candidate_name, position, employer_name, salary, start_date |
| Generic | Most relevant fields inferred from content |

### `POST /chat`
Ask a natural-language question about an uploaded document.

**Request:**
```json
{
  "session_id": "f40e847c-...",
  "query": "What is the total amount due and when is it due?"
}
```

**Response:**
```json
{
  "response": "The total amount due is $4,500.00 USD. The invoice does not specify a due date, but it was issued on January 15, 2024."
}
```

---

## Getting Started

### Prerequisites

- Java 21+
- Node.js 20+ and npm 11+
- A Google AI Studio API key (free tier available at [aistudio.google.com](https://aistudio.google.com))

### 1. Clone the repository

```bash
git clone https://github.com/your-username/DocuIntel.git
cd DocuIntel
```

### 2. Configure the backend

```bash
cd backend/src/main/resources
cp application.properties.example application.properties
```

Open `application.properties` and set your Gemini API key:

```properties
spring.ai.google.genai.api-key=YOUR_GEMINI_API_KEY
spring.ai.google.genai.embedding.api-key=YOUR_GEMINI_API_KEY
```

Or export it as an environment variable and reference it:

```properties
spring.ai.google.genai.api-key=${GEMINI_API_KEY}
```

### 3. Run the backend

```bash
cd backend
./gradlew bootRun
```

Backend starts on **http://localhost:8000**

### 4. Run the frontend

```bash
cd frontend
npm install
npm start
```

Frontend starts on **http://localhost:4200**

### 5. Open the app

Navigate to [http://localhost:4200](http://localhost:4200), upload a PDF, and explore.

---

## Key Design Decisions

**Session isolation via metadata filtering**
Every vector chunk is tagged with the uploading session's UUID. All similarity searches filter on `session_id`, so documents from different uploads never interfere with each other — even in the shared in-memory store.

**Interface-driven vector store**
All service code depends on Spring AI's `VectorStore` interface. Swapping from `SimpleVectorStore` (current, in-memory) to pgvector, Pinecone, or any other backend requires only a Gradle dependency change and new properties — zero service code changes.

**Overlapping text chunks**
Chunks use a 100-word overlap on a 500-word window. This ensures sentences that fall at chunk boundaries appear complete in at least one chunk, improving retrieval accuracy at segment edges.

**Null-safe extraction fallback**
The extraction fallback uses `HashMap` (not `Map.of()`) because `Map.of()` throws `NullPointerException` on null values. Fields missing from a document are returned as `null` rather than omitted, giving the frontend a consistent shape to render.

---

## Switching AI Providers

The backend is wired for Gemini but the `ChatClient` abstraction makes it trivial to switch.

**To use OpenAI (GPT-4o):** comment out the Gemini properties and uncomment:
```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4o
spring.ai.openai.embedding.options.model=text-embedding-3-small
```
Replace the `spring-ai-starter-model-google-genai` dependency in `build.gradle` with `spring-ai-starter-model-openai`. No Java code changes needed.

---

## Roadmap

- [ ] Persistent vector store (pgvector) for cross-session history
- [ ] Multi-page scanned PDF support via Vision OCR fallback
- [ ] Authentication and per-user session management
- [ ] Batch export to Excel / JSON
- [ ] Webhook support for automated document pipelines

