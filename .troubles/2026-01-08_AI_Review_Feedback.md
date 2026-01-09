# AI Code Review Fixes - 2026-01-08

## Issue 1: Security Vulnerability (Debug Endpoint) - [RESOLVED]

- **Problem**: `/api/ai/debug/**` is set to `permitAll()`, allowing unauthorized deletion of analysis jobs.
- **Fix**: Removed `permitAll()` for this endpoint.

## Issue 2: N+1 Query Performance (AICallbackService) - [RESOLVED]

- **Problem**: `saveSettings` and `saveEvents` perform DB queries inside loops.
- **Fix**: Refactored to batch fetch data into Maps before iterating.
  - `saveSettings`: Using `findByProjectAndNameIn`.
  - `saveEvents`: Grouping by Chapter and using `findAllByProjectAndChapter` once per chapter.

## Issue 3: Synchronous File I/O (AICallbackService) - [RESOLVED]

- **Problem**: `saveCallbackToJsonFile` is synchronous and can block/rollback transactions.
- **Fix**: Wrapped in `CompletableFuture.runAsync()`.

## Issue 4: Excessive Transaction Scope (OAuth2SuccessHandler) - [RESOLVED]

- **Problem**: `@Transactional` allows DB connection to be held during token creation and redirect.
- **Fix**: Removed `@Transactional` from `onAuthenticationSuccess` and added it to `findOrCreateGoogleUser`.
