package com.spiramindscape.backend.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Records the SQL Hibernate actually sends, so a test can assert on the shape of a query rather
 * than only on its result.
 *
 * <p>Why this exists: the goals payload looked fine for weeks — ~51 KB, no file bytes anywhere in
 * the response — while the backend was still reading every attached PDF out of the database and
 * discarding it. No assertion on the response could have caught that; only looking at the SQL can.
 *
 * <p>Usage: {@code @Import(SqlCapture.Config.class)} on the test, then {@link #start()} before the
 * call under test and {@link #stop()} after.
 */
public class SqlCapture implements StatementInspector {

    private static final List<String> STATEMENTS = Collections.synchronizedList(new ArrayList<>());
    private static volatile boolean recording;

    /** Begin recording, discarding anything captured earlier (e.g. by test setup). */
    public static void start() {
        STATEMENTS.clear();
        recording = true;
    }

    /** Stop recording and return what was captured. */
    public static List<String> stop() {
        recording = false;
        synchronized (STATEMENTS) {
            return List.copyOf(STATEMENTS);
        }
    }

    @Override
    public String inspect(String sql) {
        if (recording) {
            STATEMENTS.add(sql);
        }
        return sql; // never rewrite the statement
    }

    /** Registers the inspector with Hibernate. Import it from the tests that need SQL capture. */
    @TestConfiguration
    public static class Config {
        @Bean
        HibernatePropertiesCustomizer sqlCaptureCustomizer() {
            return properties ->
                    properties.put("hibernate.session_factory.statement_inspector", new SqlCapture());
        }
    }
}
