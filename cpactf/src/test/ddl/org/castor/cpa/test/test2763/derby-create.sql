CREATE TABLE test2763_book (
    id INTEGER PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    timestamp INTEGER NOT NULL
);

CREATE TABLE test2763_employee (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL
);