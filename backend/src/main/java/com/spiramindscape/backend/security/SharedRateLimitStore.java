package com.spiramindscape.backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate-limit counters kept in the database, so <b>one limit means one limit</b> however many
 * instances are running (BUG-057).
 *
 * <h2>What this replaces</h2>
 *
 * <p>The counters used to be a {@code ConcurrentHashMap} inside each instance, with a comment
 * admitting it: <i>"Single-instance design … If it ever scales out, swap to a shared store."</i>
 * Two things followed from that, and both got worse exactly as the app got busier:
 *
 * <ul>
 *   <li><b>The limit was multiplied by the instance count.</b> "20 chat messages a minute"
 *       meant 20 × however many instances Cloud Run happened to be running — a number that
 *       rises with load, which is to say it rose precisely when the limit was doing something.
 *       It was also unknowable: nobody could say what the limit was without looking up the
 *       current instance count.</li>
 *   <li><b>Every counter was lost when an instance was recycled</b>, which Cloud Run does
 *       constantly. A caller who hit the limit could get a fresh budget by waiting for a new
 *       instance rather than for the refill.</li>
 * </ul>
 *
 * <h2>Why the database and not something faster</h2>
 *
 * <p>Redis would be the textbook answer and is the wrong one here: it is a new managed service
 * to pay for and operate, for a table with one small row per active caller. Postgres is already
 * on the critical path of every endpoint this limiter protects — if it is unreachable the
 * request was going to fail anyway — so the limiter adds no new dependency, only one extra
 * round trip. Measured against what these endpoints already cost (a GraphQL mutation runs
 * 100–400 ms in production), that round trip is noise.
 *
 * <h2>The bucket, and why it is one statement</h2>
 *
 * <p>A classic token bucket: capacity {@code perMinute}, refilling continuously at
 * {@code perMinute} per minute. Refilling and spending happen in a <b>single</b> UPDATE whose
 * WHERE clause is the check, because two instances that each read "one token left" would each
 * spend it — the read-then-write that works in a single process is exactly what does not
 * survive being shared.
 *
 * <h2>When the database says no</h2>
 *
 * <p>A failure here <b>allows</b> the request, and logs it. Failing closed would let a blip in
 * the limiter turn into a total outage of the endpoints it guards, and there is nothing to
 * protect anyway: every one of those endpoints needs the same database to do its work.
 *
 * <p>The catch is deliberately {@code RuntimeException} and not {@code DataAccessException}.
 * The most likely failure — Postgres unreachable, or the connection pool exhausted — surfaces
 * from {@code @Transactional} as {@code CannotCreateTransactionException}, which descends from
 * {@code TransactionException} and is <b>not</b> a {@code DataAccessException}. Catching only
 * the latter would have let exactly the expected outage escape the filter and turn every
 * rate-limited endpoint into a 500 — the precise outcome this paragraph claims to prevent.
 * Catching broadly is right for a guard whose failure mode must be "step aside".
 */
@Component
public class SharedRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger("security.ratelimit");

    /**
     * How long a bucket may sit untouched before it is deleted. Well past the one-minute refill
     * window, so a deleted bucket was full anyway and its absence decides nothing differently.
     */
    static final Duration IDLE_BEFORE_PURGE = Duration.ofMinutes(30);

    /** How often the purge is worth running. */
    static final Duration PURGE_EVERY = Duration.ofMinutes(10);

    private final RateLimitBucketRepository repository;

    /**
     * When the purge last ran, epoch millis. Per-instance and approximate on purpose: several
     * instances purging is a few redundant DELETEs of rows that are already gone, which is
     * cheaper than the scheduling machinery that would coordinate them.
     */
    private final AtomicLong lastPurgeMillis = new AtomicLong(System.currentTimeMillis());

    public SharedRateLimitStore(RateLimitBucketRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean tryConsume(String key, int perMinute) {
        long now = System.currentTimeMillis();
        double tokensPerMilli = perMinute / 60_000.0;
        try {
            // The common case: the caller already has a bucket with something in it.
            if (repository.refillAndConsume(key, perMinute, tokensPerMilli, now) == 1) {
                return true;
            }
            // Zero rows is ambiguous — the bucket is empty, or this caller has none yet — and
            // the two deserve different work. Asking is one cheap indexed read; assuming
            // "absent" and letting the INSERT fail made every THROTTLED request cost an
            // update, a failed insert, a rollback and a second update, plus a line in
            // Postgres's error log. That is the wrong way round for the request a flood
            // consists of.
            if (!repository.existsById(key)) {
                ensureBucketExists(key, perMinute, now);
            }
            // Then spend through the same statement either way. The second attempt is needed
            // even when the row already existed: another instance can create it in the gap
            // between the two statements above, and treating "it exists now" as "it is empty"
            // would 429 a caller whose bucket is in fact full. That is not theoretical —
            // aCreationRaceDoesNotRefuseTheFirstRequest failed on exactly it.
            return repository.refillAndConsume(key, perMinute, tokensPerMilli, now) == 1;
        } catch (RuntimeException e) {
            // Allow, and say so. See the class note on failing open, and on why this is not
            // narrowed to DataAccessException.
            log.warn("rate_limit_store_unavailable", e);
            return true;
        }
    }

    /**
     * Makes sure the caller has a <b>full</b> bucket, creating one if not.
     *
     * <p>Full rather than "full minus the token we are about to take", so that creating and
     * spending stay separate: whoever wins the race, the row lands in the same state and
     * everyone then competes through the one UPDATE that knows how to spend.
     *
     * <p>A duplicate key is success, not failure — someone else created it first. Waiting for
     * their insert is what makes this correct rather than merely tolerant: the unique index
     * makes a second inserter <b>block</b> until the first commits, so by the time this method
     * returns the row is committed and visible to the statement that follows. The earlier shape
     * of this code spent the token during the insert and, on losing the race, retried the UPDATE
     * against a row that had not been committed yet — which silently refused one request in
     * every few dozen and is exactly what {@code concurrentRequestsDoNotOverspend} caught.
     */
    private void ensureBucketExists(String key, int perMinute, long now) {
        try {
            repository.insertBucket(key, perMinute, now);
            purgeIfDue(now);
        } catch (DataIntegrityViolationException alreadyThere) {
            // Someone beat us to it, and their row is committed now. Nothing to do.
        }
    }

    /**
     * Deletes long-idle buckets, at most once every {@link #PURGE_EVERY}.
     *
     * <p>Driven by bucket creation rather than by a scheduler, because creation is exactly when
     * the table grows, and because a {@code @Scheduled} task would be the app's first — a whole
     * mechanism to enable for one DELETE.
     *
     * <p>This is the half of the fix that is not about counting. The old map never evicted
     * anything, and for the unauthenticated endpoints the key is the client's IP address, so
     * its size was a function of how many addresses had ever sent a request — something a
     * stranger could grow on demand, in a 512 MiB container.
     */
    private void purgeIfDue(long now) {
        long last = lastPurgeMillis.get();
        if (now - last < PURGE_EVERY.toMillis()) return;
        if (!lastPurgeMillis.compareAndSet(last, now)) return;   // another thread is on it
        try {
            int deleted = repository.purgeIdleSince(now - IDLE_BEFORE_PURGE.toMillis());
            if (deleted > 0) log.info("rate_limit_buckets_purged count={}", deleted);
        } catch (RuntimeException e) {
            // Housekeeping must never fail the request it rode in on.
            log.warn("rate_limit_purge_failed", e);
        }
    }
}
