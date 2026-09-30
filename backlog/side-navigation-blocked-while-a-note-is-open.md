# BUG-088 — The side navigation cannot be used while a note is open

**Status:** ✅ Fixed (2026-09-15, awaiting the owner's live check)
**Area:** Resources · Web shell
**Severity:** Low — navigating away needs an extra click to close the note first

## Summary

With a resource preview open on a laptop, clicks on the side navigation and the header do nothing
(owner, 2026-09-15).

## Steps to reproduce (before the fix)

1. On a laptop, open a note in a goal's Resources.
2. Click an item in the side navigation — nothing happens; the click closes the preview instead.

## Root cause

`ResourceBackdrop` (`Resources.tsx`) is a full-screen `fixed inset-0 z-[35]` layer portalled to
`document.body`. `SideNav` and the app header are `sticky z-30`, so the backdrop sits on top of them.

## Fix

The side navigation and the header are `z-[36]`: above the backdrop, below the chat (`z-40`) and the
preview panel (`z-50`).

## How to verify

Open a note on a laptop and click a side-navigation item: the app navigates.

## Resolution

Fixed as described.
