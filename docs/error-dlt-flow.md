# Error Handling & DLT Flow Diagram

## Przepływ obsługi błędów z Dead Letter Topic

```mermaid
flowchart TD
    subgraph Consumer["Consumer (Validation/Execution/Notification)"]
        C1[Odbierz wiadomość z Kafka]
        C2[Przetwarzaj wiadomość]
        C3{Błąd?}
        C4[ACK - sukces]
    end

    subgraph ErrorHandler
        EH1[handleError]
        EH2[Klasyfikuj błąd<br/>ErrorClassifier]
    end

    subgraph ErrorClassifier
        EC1{Typ wyjątku?}
        EC2[BUSINESS_VALIDATION<br/>maxRetries: 0]
        EC3[TECHNICAL_TRANSIENT<br/>maxRetries: 5]
        EC4[DESERIALIZATION<br/>maxRetries: 0]
        EC5[UNKNOWN<br/>maxRetries: 1]
    end

    subgraph RetryLogic
        R1{Próba < maxRetries?}
        R2[Oblicz delay:<br/>initialDelay × 2^attempt]
        R3[Thread.sleep delay]
        R4[NIE rób ACK<br/>Kafka ponowi]
    end

    subgraph DeadLetterTopicService
        DLT1[Utwórz FailedMessage]
        DLT2[Dodaj metadane:<br/>- originalTopic<br/>- partition/offset<br/>- exception details<br/>- stackTrace<br/>- attemptCount<br/>- errorCategory]
        DLT3[Wyślij do topic-dlt]
        DLT4[ACK oryginalną wiadomość]
    end

    subgraph DLT Topics
        DLT_V[(transfer-validation-dlt)]
        DLT_E[(transfer-execution-dlt)]
        DLT_C[(transfer-completed-dlt)]
    end

    subgraph DltMonitorConsumer
        MON1[Konsumuj z *-dlt topics]
        MON2[Aktualizuj statystyki]
        MON3[Log szczegóły błędu]
    end

    subgraph DltStatsController
        API1[GET /api/dlt/stats]
        API2[GET /api/dlt/health]
    end

    %% Main flow
    C1 --> C2 --> C3
    C3 -->|Nie| C4
    C3 -->|Tak| EH1

    %% Error classification
    EH1 --> EH2 --> EC1
    EC1 -->|AccountNotFoundException<br/>InsufficientFundsException<br/>InvalidAccountException| EC2
    EC1 -->|SQLException<br/>ConnectException<br/>SocketTimeoutException| EC3
    EC1 -->|JsonParseException<br/>MessageConversionException| EC4
    EC1 -->|Inne| EC5

    %% Retry decision
    EC2 & EC3 & EC4 & EC5 --> R1
    R1 -->|Tak - retry| R2 --> R3 --> R4
    R1 -->|Nie - max retries| DLT1

    %% DLT flow
    DLT1 --> DLT2 --> DLT3 --> DLT4
    DLT3 --> DLT_V & DLT_E & DLT_C

    %% Monitoring
    DLT_V & DLT_E & DLT_C --> MON1 --> MON2 --> MON3
    MON2 --> API1 & API2

    %% Styling
    classDef error fill:#ff8787,stroke:#c92a2a,color:#000
    classDef retry fill:#ffd43b,stroke:#fab005,color:#000
    classDef dlt fill:#ff6b6b,stroke:#c92a2a,color:#fff
    classDef monitor fill:#4dabf7,stroke:#1864ab,color:#fff
    classDef success fill:#69db7c,stroke:#2f9e44,color:#fff

    class EH1,EH2,EC1,EC2,EC3,EC4,EC5 error
    class R1,R2,R3,R4 retry
    class DLT1,DLT2,DLT3,DLT4,DLT_V,DLT_E,DLT_C dlt
    class MON1,MON2,MON3,API1,API2 monitor
    class C4 success
```

## Klasyfikacja błędów i strategia retry

```mermaid
flowchart LR
    subgraph BusinessValidation["BUSINESS_VALIDATION"]
        B1[AccountNotFoundException]
        B2[InsufficientFundsException]
        B3[InvalidAccountException]
        B4[IllegalArgumentException]
    end

    subgraph TechnicalTransient["TECHNICAL_TRANSIENT"]
        T1[SQLException]
        T2[ConnectException]
        T3[SocketTimeoutException]
        T4[LockTimeoutException]
    end

    subgraph Deserialization["DESERIALIZATION"]
        D1[JsonParseException]
        D2[MessageConversionException]
    end

    subgraph Unknown["UNKNOWN"]
        U1[Wszystkie inne wyjątki]
    end

    BusinessValidation -->|maxRetries: 0<br/>Natychmiast do DLT| DLT[(DLT)]
    TechnicalTransient -->|maxRetries: 5<br/>Exponential backoff:<br/>1s, 2s, 4s, 8s, 16s| RETRY{Retry?}
    RETRY -->|sukces| OK[Sukces]
    RETRY -->|max retries| DLT
    Deserialization -->|maxRetries: 0<br/>Natychmiast do DLT| DLT
    Unknown -->|maxRetries: 1<br/>500ms delay| RETRY2{Retry?}
    RETRY2 -->|sukces| OK
    RETRY2 -->|max retries| DLT

    classDef business fill:#ffa94d,stroke:#e8590c
    classDef technical fill:#74c0fc,stroke:#1971c2
    classDef deser fill:#f06595,stroke:#c2255c
    classDef unknown fill:#adb5bd,stroke:#495057
    classDef dlt fill:#ff6b6b,stroke:#c92a2a,color:#fff

    class B1,B2,B3,B4 business
    class T1,T2,T3,T4 technical
    class D1,D2 deser
    class U1 unknown
    class DLT dlt
```

## Exponential Backoff - szczegóły

```mermaid
flowchart TD
    START[Błąd TECHNICAL_TRANSIENT] --> A1[Attempt 1]
    A1 -->|Fail| W1[Wait 1s]
    W1 --> A2[Attempt 2]
    A2 -->|Fail| W2[Wait 2s]
    W2 --> A3[Attempt 3]
    A3 -->|Fail| W3[Wait 4s]
    W3 --> A4[Attempt 4]
    A4 -->|Fail| W4[Wait 8s]
    W4 --> A5[Attempt 5]
    A5 -->|Fail| W5[Wait 16s]
    W5 --> A6[Attempt 6 - ostatnia]
    A6 -->|Fail| DLT[Send to DLT]

    A1 & A2 & A3 & A4 & A5 & A6 -->|Success| OK[Sukces - ACK]

    style DLT fill:#ff6b6b,stroke:#c92a2a,color:#fff
    style OK fill:#69db7c,stroke:#2f9e44,color:#fff
```

## Struktura FailedMessage w DLT

```mermaid
classDiagram
    class FailedMessage {
        +String originalTopic
        +Integer originalPartition
        +Long originalOffset
        +String originalKey
        +String originalValue
        +String exceptionType
        +String exceptionMessage
        +String stackTrace
        +Integer attemptCount
        +Instant failedAt
        +String consumerGroupId
        +Map~String,String~ headers
        +ErrorCategory errorCategory
        +boolean retryable
    }

    class ErrorCategory {
        <<enumeration>>
        BUSINESS_VALIDATION
        TECHNICAL_TRANSIENT
        DESERIALIZATION
        UNKNOWN
        +int maxRetries
        +Duration initialDelay
        +boolean autoRetryFromDlt
    }

    FailedMessage --> ErrorCategory
```

## Sekwencja obsługi błędu

```mermaid
sequenceDiagram
    participant K as Kafka Topic
    participant C as Consumer
    participant EH as ErrorHandler
    participant EC as ErrorClassifier
    participant DLT as DeadLetterTopicService
    participant KDLT as Kafka DLT
    participant MON as DltMonitorConsumer

    K->>C: Wiadomość
    C->>C: Przetwarzaj
    C->>C: ❌ Wyjątek!

    C->>EH: handleError(exception, record)
    EH->>EC: classify(exception)
    EC-->>EH: BUSINESS_VALIDATION (maxRetries=0)

    Note over EH: attemptCount >= maxRetries<br/>Nie ma sensu retry

    EH->>DLT: sendToDeadLetterTopic(record, exception)
    DLT->>DLT: Utwórz FailedMessage
    DLT->>KDLT: Wyślij do transfer-validation-dlt
    DLT-->>EH: OK

    EH->>C: shouldAck = true
    C->>K: ACK (offset committed)

    Note over K: Wiadomość usunięta z głównego topic<br/>Zapisana w DLT do analizy

    KDLT->>MON: Konsumuj z DLT
    MON->>MON: Aktualizuj statystyki
    MON->>MON: Log szczegóły błędu
```

## Monitorowanie DLT - REST API

| Endpoint | Opis | Odpowiedź |
|----------|------|-----------|
| `GET /api/dlt/stats` | Statystyki DLT | `{totalSentToDlt, byTopic, byCategory}` |
| `GET /api/dlt/health` | Health check | `{status, totalDltMessages, threshold}` |

### Przykład odpowiedzi `/api/dlt/stats`:

```json
{
  "totalSentToDlt": 5,
  "byTopic": {
    "transfer-validation": 3,
    "transfer-execution": 2
  },
  "byCategory": {
    "BUSINESS_VALIDATION": 4,
    "TECHNICAL_TRANSIENT": 1
  }
}
```

## Kluczowe zasady DLT

| Zasada | Opis |
|--------|------|
| **Nie blokuj partycji** | Błędne wiadomości trafiają do DLT, nie blokują kolejki |
| **Zachowaj kontekst** | FailedMessage zawiera wszystkie informacje do debugowania |
| **Retencja 30 dni** | DLT topics mają dłuższą retencję niż główne |
| **Monitorowanie** | Alerting gdy >100 wiadomości w DLT |
| **Separacja kategorii** | Różne strategie dla różnych typów błędów |
