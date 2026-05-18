UPDATE resource
SET title = LEFT(title, 20)
WHERE title IS NOT NULL AND CHAR_LENGTH(title) > 20;

UPDATE resource
SET name = LEFT(name, 20)
WHERE name IS NOT NULL AND CHAR_LENGTH(name) > 20;

ALTER TABLE resource
    ALTER COLUMN title TYPE VARCHAR(20),
    ALTER COLUMN name TYPE VARCHAR(20);
