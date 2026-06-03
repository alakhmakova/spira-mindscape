package com.spiramindscape.backend.googledocs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request to export a note as a Google Doc. {@code html} is the note's rendered HTML. */
public record CreateDocRequest(

        @Size(max = 200, message = "title must be 200 characters or fewer")
        String title,

        @NotBlank(message = "html is required")
        @Size(max = 500_000, message = "html is too large")
        String html
) {}
