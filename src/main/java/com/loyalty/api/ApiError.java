package com.loyalty.api;

/** Error body returned by every failing request: a stable machine-readable code plus a readable message. */
public record ApiError(String error, String message) {
}
