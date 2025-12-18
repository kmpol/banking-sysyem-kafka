# Transfer Flow Diagram

## Główny przepływ transferu (Happy Path)

```mermaid
flowchart TD
    subgraph Client
        A[POST /api/transfers]
    end

    subgraph TransferService
        B[Generuj UUID transferId]
        C[Utwórz Transfer<br/>status: PENDING]
        D[Zapisz do DB]
        E[Zapisz do Outbox<br/>destination: transfer-validation]
        F[Return 201 Created]
    end

    subgraph OutboxProcessor
        G[Pobierz nieprzetworzony event]
        H[Wyślij do Kafka]
        I[Oznacz jako processed=true]
    end

    subgraph Kafka Topics
        K1[(transfer-validation)]
        K2[(transfer-execution)]
        K3[(transfer-completed)]
    end

    subgraph ValidationConsumer
        V1[Odbierz wiadomość]
        V2{Status już<br/>VALIDATED?}
        V3[Sprawdź czy konta istnieją]
        V4[Sprawdź czy aktywne]
        V5[Sprawdź saldo]
        V6[Status: VALIDATED]
        V7[Zapisz do Outbox<br/>destination: transfer-execution]
        V8[ACK]
    end

    subgraph ExecutionConsumer
        E1[Odbierz wiadomość]
        E2{Status już<br/>COMPLETED?}
        E3[SELECT FOR UPDATE<br/>na kontach]
        E4[fromAccount.withdraw]
        E5[toAccount.deposit]
        E6[Status: COMPLETED]
        E7[Zapisz do Outbox<br/>destination: transfer-completed]
        E8[ACK + Release locks]
    end

    subgraph NotificationConsumer
        N1[Odbierz wiadomość]
        N2[Wyślij powiadomienie<br/>email/SMS/push]
        N3[ACK]
    end

    subgraph AuditConsumer
        AU[Log dla compliance]
    end

    %% Main Flow
    A --> B --> C --> D --> E --> F

    %% Outbox to Validation
    E -.->|co 3s| G
    G --> H --> I
    H --> K1

    %% Validation Consumer
    K1 --> V1 --> V2
    V2 -->|Tak - skip| V7
    V2 -->|Nie| V3 --> V4 --> V5 --> V6 --> V7 --> V8

    %% Outbox to Execution
    V7 -.->|co 3s| G
    H --> K2

    %% Execution Consumer
    K2 --> E1 --> E2
    E2 -->|Tak - skip| E7
    E2 -->|Nie| E3 --> E4 --> E5 --> E6 --> E7 --> E8

    %% Outbox to Completed
    E7 -.->|co 3s| G
    H --> K3

    %% Notification Consumer
    K3 --> N1 --> N2 --> N3

    %% Audit parallel
    K1 & K2 & K3 -.-> AU

    %% Styling
    classDef kafka fill:#ff6b6b,stroke:#c92a2a,color:#fff
    classDef service fill:#4dabf7,stroke:#1864ab,color:#fff
    classDef consumer fill:#69db7c,stroke:#2f9e44,color:#fff
    classDef outbox fill:#ffd43b,stroke:#fab005,color:#000

    class K1,K2,K3 kafka
    class B,C,D,E,F service
    class V1,V2,V3,V4,V5,V6,V7,V8,E1,E2,E3,E4,E5,E6,E7,E8,N1,N2,N3 consumer
    class G,H,I outbox
```

## Status Transfer - Maszyna stanów

```mermaid
stateDiagram-v2
    [*] --> PENDING: POST /api/transfers
    PENDING --> VALIDATING: ValidationConsumer start
    VALIDATING --> VALIDATED: Walidacja OK
    VALIDATING --> FAILED: Błąd biznesowy → DLT
    VALIDATED --> EXECUTING: ExecutionConsumer start
    EXECUTING --> COMPLETED: Transfer wykonany
    EXECUTING --> FAILED: Błąd wykonania → DLT
    COMPLETED --> [*]
    FAILED --> [*]
```

## Sekwencja czasowa

```mermaid
sequenceDiagram
    participant C as Client
    participant API as TransferController
    participant TS as TransferService
    participant DB as Database
    participant OP as OutboxProcessor
    participant K as Kafka
    participant VC as ValidationConsumer
    participant EC as ExecutionConsumer
    participant NC as NotificationConsumer

    C->>API: POST /api/transfers
    API->>TS: initiateTransfer()
    TS->>DB: Save Transfer (PENDING)
    TS->>DB: Save OutboxEvent
    TS-->>C: 201 Created + transferId

    Note over OP: Co 3 sekundy
    OP->>DB: SELECT unprocessed events
    OP->>K: Send to transfer-validation
    OP->>DB: Mark as processed

    K->>VC: Consume message
    VC->>DB: Validate accounts & balance
    VC->>DB: Update status → VALIDATED
    VC->>DB: Save OutboxEvent
    VC->>K: ACK

    OP->>K: Send to transfer-execution

    K->>EC: Consume message
    EC->>DB: SELECT FOR UPDATE (lock accounts)
    EC->>DB: Withdraw from source
    EC->>DB: Deposit to target
    EC->>DB: Update status → COMPLETED
    EC->>DB: Save OutboxEvent
    EC->>K: ACK (release locks)

    OP->>K: Send to transfer-completed

    K->>NC: Consume message
    NC->>NC: Send notification
    NC->>K: ACK
```

## Kluczowe cechy

| Cecha | Implementacja |
|-------|--------------|
| **Atomowość** | Outbox Pattern - DB + Event w jednej transakcji |
| **Idempotencja** | UUID transferId + sprawdzanie statusu |
| **Exactly-once** | Manual ACK + idempotency checks |
| **Spójność danych** | Pessimistic locking (SELECT FOR UPDATE) |
| **Kolejność** | Partycjonowanie po fromAccountNumber |
| **Audyt** | Osobny consumer group dla audit-group |
