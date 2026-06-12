package com.spiramindscape.backend.ai.grow;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * JDBC access to the {@code book_chunk} table (GROW coaching library).
 *
 * <p>Deliberately NOT a JPA entity: the {@code embedding} column uses the
 * pgvector {@code vector} type, which H2 (used by the integration tests with
 * Hibernate DDL) cannot represent. The table exists only via Flyway
 * ({@code V13__grow_books.sql}) and is accessed with plain SQL.
 */
@Repository
public class BookChunkRepository {

    /** A chunk awaiting its embedding. */
    public record UnembeddedChunk(long id, String content) {}

    /** A retrieval hit: where it came from and the passage itself. */
    public record FoundChunk(String book, int ord, String content) {}

    private final JdbcTemplate jdbc;

    public BookChunkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countForBook(String book) {
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM book_chunk WHERE book = ?", Long.class, book);
        return n == null ? 0 : n;
    }

    public long countAll() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM book_chunk", Long.class);
        return n == null ? 0 : n;
    }

    public long countUnembedded() {
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM book_chunk WHERE embedding IS NULL", Long.class);
        return n == null ? 0 : n;
    }

    /** Inserts a book's chunks in order, embeddings left NULL (filled lazily). */
    public void insertChunks(String book, List<String> contents) {
        jdbc.batchUpdate(
                "INSERT INTO book_chunk (book, ord, content) VALUES (?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setString(1, book);
                        ps.setInt(2, i);
                        ps.setString(3, contents.get(i));
                    }

                    @Override
                    public int getBatchSize() {
                        return contents.size();
                    }
                });
    }

    public List<UnembeddedChunk> findUnembedded(int limit) {
        return jdbc.query(
                "SELECT id, content FROM book_chunk WHERE embedding IS NULL ORDER BY id LIMIT ?",
                (rs, i) -> new UnembeddedChunk(rs.getLong("id"), rs.getString("content")),
                limit);
    }

    /** Stores embeddings for the given chunk ids ({@code ids.get(i)} ↔ {@code vectors.get(i)}). */
    public void saveEmbeddings(List<Long> ids, List<float[]> vectors, String model) {
        if (ids.size() != vectors.size()) {
            throw new IllegalArgumentException(
                    "ids and vectors size mismatch: " + ids.size() + " vs " + vectors.size());
        }
        jdbc.batchUpdate(
                "UPDATE book_chunk SET embedding = CAST(? AS vector), embedding_model = ? WHERE id = ?",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setString(1, toVectorLiteral(vectors.get(i)));
                        ps.setString(2, model);
                        ps.setLong(3, ids.get(i));
                    }

                    @Override
                    public int getBatchSize() {
                        return ids.size();
                    }
                });
    }

    /** Top-{@code k} chunks by cosine distance to the query vector (exact scan). */
    public List<FoundChunk> search(float[] queryVector, int k) {
        return jdbc.query(
                "SELECT book, ord, content FROM book_chunk "
                        + "WHERE embedding IS NOT NULL "
                        + "ORDER BY embedding <=> CAST(? AS vector) LIMIT ?",
                (rs, i) -> new FoundChunk(
                        rs.getString("book"), rs.getInt("ord"), rs.getString("content")),
                toVectorLiteral(queryVector), k);
    }

    /** pgvector input literal: {@code [0.1,0.2,…]}. */
    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
