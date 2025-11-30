MERGE INTO accounts (id, account_number, owner_name, balance, active, created_at, version)
KEY(account_number) VALUES
    (1, 'ACC001', 'John Doe', 10000.00, true, CURRENT_TIMESTAMP, 0),
    (2, 'ACC002', 'Jane Smith', 5000.00, true, CURRENT_TIMESTAMP, 0),
    (3, 'ACC003', 'Bob Johnson', 15000.00, true, CURRENT_TIMESTAMP, 0),
    (4, 'ACC004', 'Alice Williams', 2000.00, true, CURRENT_TIMESTAMP, 0),
    (5, 'ACC005', 'Charlie Brown', 500.00, true, CURRENT_TIMESTAMP, 0);