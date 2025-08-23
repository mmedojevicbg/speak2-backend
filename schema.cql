-- ScyllaDB Schema for speak2 application
-- Run these commands in cqlsh or ScyllaDB client

-- Create keyspace
CREATE KEYSPACE IF NOT EXISTS speak2
WITH replication = {
    'class': 'SimpleStrategy',
    'replication_factor': 1
};

USE speak2;

-- Create chat_messages table
CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID,
    chat_id UUID,
    sender_id UUID,
    message TEXT,
    sent_at TIMESTAMP,
    PRIMARY KEY (chat_id, sent_at, id)
) WITH CLUSTERING ORDER BY (sent_at ASC);

-- Create index for queries by message id
CREATE INDEX IF NOT EXISTS ON chat_messages (id);