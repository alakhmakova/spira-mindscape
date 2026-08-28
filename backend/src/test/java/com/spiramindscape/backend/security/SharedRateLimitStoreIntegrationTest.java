package com.spiramindscape.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **One limit means one limit, however many instances are running** (BUG-057).
 *
 * <p>The counters used to be a {@code ConcurrentHashMap} inside each instance, and the comment
 * on it said so: <i>"Single-instance design … If it ever scales out, swap to a shared store."</i>
 * That made the real limit "N per minute × the number of live Cloud Run instances" — a number
 * that goes up under load, which is to say it went up exactly when the limit was doing
 * something — and it reset every counter whenever Cloud Run recycled an instance.
 *
 * <p>None of that could fail a test, because the old test only ever created one filter in one
 * JVM. {@link #twoInstancesShareOneLimit()} is the case that could not be written before and is
 * the reason this class exists: two independent {@code SharedRateLimitStore} objects stand in
 * for two instances, and together they must still allow only the documented number.
 *
 * <p>Runs against the real database (H2 in PostgreSQL mode, as every other integration test
 * here does) so the native SQL — the refill arithmetic, the atomic consume, the duplicate-key
 * race, the purge — is the code under test rather than a mock of it.
 */
@SpringBootTest
@ActiveProfiles("test")
class SharedRateLimitStoreIntegrationTest {

    @Autowired private RateLimitBucketRepository repository;

    private SharedRateLimitStore store;

    @BeforeEach
    void freshTable() {
        repository.deleteAll();
        store = new SharedRateLimitStore(repository);
    }

    // ─── The behaviour the limiter promises ───────────────────────────────────

    @Test
    @DisplayName("Requests within the limit pass, and the one over it does not")
    void allowsUpToTheLimit() {
        for (int i = 0; i < 5; i++) {
            assertThat(store.tryConsume("ai-chat:u:1", 5))
                    .as("request %d of 5", i + 1).isTrue();
        }
        assertThat(store.tryConsume("ai-chat:u:1", 5)).isFalse();
    }

    @Test
    @DisplayName("Different callers have independent buckets")
    void callersAreIndependent() {
        for (int i = 0; i < 5; i++) store.tryConsume("ai-chat:u:1", 5);

        assertThat(store.tryConsume("ai-chat:u:1", 5)).isFalse();
        assertThat(store.tryConsume("ai-chat:u:2", 5)).isTrue();
        assertThat(store.tryConsume("ai-chat:ip:1.2.3.4", 5)).isTrue();
    }

    @Test
    @DisplayName("Different limits on the same caller do not share a bucket")
    void limitsAreIndependent() {
        // The key is "<limit>:<caller>", so spending someone's chat budget must not spend
        // their GraphQL budget — they are different resources with different costs.
        for (int i = 0; i < 5; i++) store.tryConsume("ai-chat:u:1", 5);

        assertThat(store.tryConsume("ai-chat:u:1", 5)).isFalse();
        assertThat(store.tryConsume("graphql:u:1", 5)).isTrue();
    }

    // ─── The case that could not be written before ────────────────────────────

    @Test
    @DisplayName("Two instances share one limit rather than getting one each")
    void twoInstancesShareOneLimit() {
        // Two stores over one database == two Cloud Run instances. With the old in-memory
        // buckets this test would let ten requests through against a limit of five, which is
        // precisely what production did and nothing noticed.
        SharedRateLimitStore instanceA = new SharedRateLimitStore(repository);
        SharedRateLimitStore instanceB = new SharedRateLimitStore(repository);

        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            SharedRateLimitStore instance = (i % 2 == 0) ? instanceA : instanceB;
            if (instance.tryConsume("ai-chat:u:7", 5)) allowed++;
        }

        assertThat(allowed).isEqualTo(5);
    }

    @Test
    @DisplayName("A restarted instance does not hand the caller a fresh budget")
    void aNewInstanceInheritsTheSpentBudget() {
        // Cloud Run recycles instances constantly. With per-instance counters, waiting for a
        // new one was a way around the limit — no refill required.
        for (int i = 0; i < 5; i++) store.tryConsume("ai-chat:u:9", 5);

        SharedRateLimitStore afterRestart = new SharedRateLimitStore(repository);

        assertThat(afterRestart.tryConsume("ai-chat:u:9", 5)).isFalse();
    }

    // ─── Refill ───────────────────────────────────────────────────────────────

    // Time is set explicitly here rather than waited out. A sleep-based refill test is both
    // slow and a liar: the loop that empties the bucket takes real milliseconds, during which
    // the bucket is already refilling, so what such a test proves depends on how fast the
    // machine ran it. Seeding `refilled_at_millis` states the elapsed time exactly.

    /** An empty bucket that was last touched {@code agoMillis} ago. */
    private void seedEmptyBucket(String key, long agoMillis) {
        repository.insertBucket(key, 0, System.currentTimeMillis() - agoMillis);
    }

    @Test
    @DisplayName("A bucket refills over time")
    void refillsOverTime() {
        // 60 per minute is one per second, so thirty seconds owes thirty tokens.
        seedEmptyBucket("graphql:u:3", 30_000);

        for (int i = 0; i < 30; i++) {
            assertThat(store.tryConsume("graphql:u:3", 60))
                    .as("token %d of the 30 that thirty seconds is worth", i + 1).isTrue();
        }
        assertThat(store.tryConsume("graphql:u:3", 60)).isFalse();
    }

    @Test
    @DisplayName("A refill is fractional, so a short wait is not rounded away to nothing")
    void refillIsFractional() {
        // Half a second against 60-per-minute is worth half a token: not enough on its own,
        // and it must not be lost either. Truncating to whole tokens would make every limit
        // quietly stricter than it says, and the shorter the wait the worse the error.
        seedEmptyBucket("graphql:u:8", 500);
        assertThat(store.tryConsume("graphql:u:8", 60)).isFalse();

        // Another half-second's worth, added to the half already banked, is a whole token.
        repository.purgeIdleSince(Long.MAX_VALUE);
        seedEmptyBucket("graphql:u:8", 1_000);
        assertThat(store.tryConsume("graphql:u:8", 60)).isTrue();
    }

    @Test
    @DisplayName("A refill never exceeds the bucket's capacity")
    void refillIsCappedAtCapacity() {
        // Without the cap, an idle caller would bank tokens for as long as they stayed quiet
        // and could then spend an hour's worth in one burst — the exact thing a rate limit is
        // there to prevent. An hour idle against 3-per-minute would be 180 tokens; the cap
        // says 3.
        seedEmptyBucket("ai-chat:u:4", 3_600_000);

        assertThat(store.tryConsume("ai-chat:u:4", 3)).isTrue();
        assertThat(store.tryConsume("ai-chat:u:4", 3)).isTrue();
        assertThat(store.tryConsume("ai-chat:u:4", 3)).isTrue();
        assertThat(store.tryConsume("ai-chat:u:4", 3)).isFalse();
    }

    @Test
    @DisplayName("A brand-new caller gets the full limit, not one short of it")
    void aNewCallerGetsTheWholeBudget() {
        // The bucket is created full and the token is then taken by the same statement that
        // takes every other token. An earlier version created it with the first token already
        // spent, which made creating and spending two different code paths — and the seam
        // between them lost a request whenever two callers raced.
        for (int i = 0; i < 5; i++) {
            assertThat(store.tryConsume("ai-chat:u:new", 5))
                    .as("request %d of 5 for a caller seen for the first time", i + 1).isTrue();
        }
        assertThat(store.tryConsume("ai-chat:u:new", 5)).isFalse();
    }

    // ─── Concurrency ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Concurrent requests for one caller cannot overspend the bucket")
    void concurrentRequestsDoNotOverspend() throws Exception {
        // The reason refill and consume are one statement: read-then-write lets two threads
        // both see "one token left" and both take it.
        int capacity = 10;
        int attempts = 40;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> calls = IntStream.range(0, attempts)
                    .<Callable<Boolean>>mapToObj(i -> () -> store.tryConsume("ai-chat:u:5", capacity))
                    .toList();

            long allowed = pool.invokeAll(calls).stream()
                    .filter(SharedRateLimitStoreIntegrationTest::get)
                    .count();

            // A little slack upwards only for the refill that genuinely accrues while the
            // threads run; never the 40 that an unsynchronised bucket would have allowed.
            assertThat(allowed).isBetween((long) capacity, (long) capacity + 2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Two instances racing to create the same new bucket both get an answer")
    void aCreationRaceDoesNotRefuseTheFirstRequest() throws Exception {
        // Both instances find no bucket and both try to insert; the loser gets a duplicate-key
        // violation. Refusing on that would 429 a caller on their very first request.
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> calls = IntStream.range(0, 4)
                    .<Callable<Boolean>>mapToObj(i ->
                            () -> new SharedRateLimitStore(repository).tryConsume("login:ip:8.8.8.8", 10))
                    .toList();

            long allowed = pool.invokeAll(calls).stream()
                    .filter(SharedRateLimitStoreIntegrationTest::get)
                    .count();

            assertThat(allowed).isEqualTo(4);
        } finally {
            pool.shutdownNow();
        }
    }

    // ─── Eviction: the other half of the fix ──────────────────────────────────

    @Test
    @DisplayName("Idle buckets are deleted, so the table does not grow with every caller ever seen")
    void idleBucketsArePurged() {
        // The in-memory map never evicted anything, and for the unauthenticated endpoints the
        // key is the client's IP — so its size was a function of how many addresses had ever
        // sent a request, which a stranger can grow on demand. This is that leak's fix.
        long stale = System.currentTimeMillis()
                - SharedRateLimitStore.IDLE_BEFORE_PURGE.toMillis() - 1;
        repository.insertBucket("client-errors:ip:5.5.5.5", 10, stale);
        repository.insertBucket("client-errors:ip:6.6.6.6", 10, stale);
        assertThat(repository.count()).isEqualTo(2);

        int deleted = repository.purgeIdleSince(
                System.currentTimeMillis() - SharedRateLimitStore.IDLE_BEFORE_PURGE.toMillis());

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("A bucket still in use is not purged out from under its caller")
    void activeBucketsSurviveThePurge() {
        store.tryConsume("ai-chat:u:6", 5);

        repository.purgeIdleSince(
                System.currentTimeMillis() - SharedRateLimitStore.IDLE_BEFORE_PURGE.toMillis());

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("The purge window is comfortably longer than a bucket takes to refill")
    void thePurgeWindowCannotForgetASpentBucket() {
        // A bucket deleted before it had refilled would hand its caller a full budget early —
        // the same hole the instance recycling used to open.
        assertThat(SharedRateLimitStore.IDLE_BEFORE_PURGE.toMinutes()).isGreaterThan(1);
        assertThat(SharedRateLimitStore.PURGE_EVERY)
                .isLessThan(SharedRateLimitStore.IDLE_BEFORE_PURGE);
    }

    private static boolean get(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
