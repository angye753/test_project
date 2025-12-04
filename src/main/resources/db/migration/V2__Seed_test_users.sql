-- Password hashes are bcrypt hashes of "password123"
INSERT INTO users (username, password_hash, role) VALUES
    ('holder1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36LRW95o', 'ACCOUNT_HOLDER'),
    ('holder2', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36LRW95o', 'ACCOUNT_HOLDER'),
    ('operator1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36LRW95o', 'OPERATOR'),
    ('admin1', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36LRW95o', 'ADMIN');

INSERT INTO accounts (holder_name, balance_amount, balance_currency, status) VALUES
    ('John Doe', 10000.00, 'USD', 'ACTIVE'),
    ('Jane Smith', 5000.00, 'USD', 'ACTIVE'),
    ('Test Account', 1000.00, 'USD', 'ACTIVE');
