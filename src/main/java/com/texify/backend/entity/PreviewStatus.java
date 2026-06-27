package com.texify.backend.entity;

/**
 * Lifecycle state of a template preview.
 * <p>
 * Lets the frontend decide whether to show a placeholder, a spinner, or the
 * actual preview image. Stored as a string on {@code templates.preview_status}.
 * </p>
 */
public enum PreviewStatus {

    /** No preview yet — never generated. */
    PENDING,

    /** Asynchronous generation in progress (frontend shows a spinner). */
    GENERATING,

    /** Preview available and up to date. */
    READY,

    /** Blocks were modified since the last generation — preview is stale. */
    OUTDATED,

    /** The last generation attempt failed. */
    ERROR
}
