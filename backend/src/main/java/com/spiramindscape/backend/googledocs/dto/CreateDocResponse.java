package com.spiramindscape.backend.googledocs.dto;

/** The created Google Doc's shareable link, which the SPA opens in a new tab. */
public record CreateDocResponse(String webViewLink) {}
