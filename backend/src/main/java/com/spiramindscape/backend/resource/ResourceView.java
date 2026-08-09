package com.spiramindscape.backend.resource;

import java.time.Instant;

/**
 * A resource without its file bytes, used by every list-shaped read (the {@code goals} graph and
 * {@code resourcesByGoal}).
 *
 * <p>{@link Resource#getDataUrl()} is an eagerly-loaded {@code TEXT} column holding a base64 data
 * URL — up to 5 MB decoded, ~6.7 MB encoded. Selecting whole {@code Resource} entities therefore
 * dragged every attached PDF out of the database on every goals fetch, even though no client asks
 * for {@code dataUrl} there: the bytes were read across the network and then discarded during
 * serialization. That is invisible in the response size (the goals payload is ~51 KB) but dominated
 * Neon's metered egress — see {@code backlog/background-sync-refetches-full-goals-egress.md}.
 *
 * <p>Clients load the bytes on demand through the {@code resourceById} query, which still returns
 * the full entity; the web store already treats a null {@code dataUrl} as "not loaded yet"
 * ({@code store.ts loadResourceFile}).
 */
public record ResourceView(
        Long id,
        String type,
        String title,
        String body,
        String url,
        String mime,
        String name,
        String role,
        String email,
        String phone,
        String driveWebViewLink,
        Long goalId,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Always {@code null} — the whole point of this projection is that {@code data_url} is never
     * selected. Declared so the GraphQL {@code Resource.dataUrl} field still resolves (to null)
     * rather than failing to find a property.
     */
    public String dataUrl() {
        return null;
    }
}
