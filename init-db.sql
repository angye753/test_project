-- Ensure the bankdb database exists and is properly initialized
-- This script runs during PostgreSQL container initialization

-- Create the pgcrypto extension for UUID support
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
