/**
 * Canonical maximum lengths for user-editable text fields.
 *
 * These MUST mirror the server-side limits so a value that passes the client can
 * never be rejected by the backend (which would surface as an opaque "sync failed"
 * banner). Sources of truth on the server:
 *   - Goal.title / Goal.description  → GoalService.MAX_GOAL_TITLE_LENGTH (200) /
 *                                       MAX_GOAL_DESCRIPTION_LENGTH (5000)
 *   - Target.title / Target.unit     → Target @Column(length = 200) / @Column(length = 50)
 *   - Option.text                    → Option @Size(max = 500)
 *   - RealityItem.text               → RealityItem.MAX_REALITY_ITEM_TEXT_LENGTH (500)
 *   - ChecklistItem.text             → ChecklistItem @Size(max = 500)
 *   - Resource.title / name / role / email
 *                                    → ResourceService.MAX_RESOURCE_LABEL_LENGTH (200)
 *   - Resource.body (note)           → ResourceService.MAX_NOTE_BODY_LENGTH (50000)
 *   - Resource.url (link)            → ResourceService.MAX_LINK_URL_LENGTH (1000)
 *   - Resource.phone                 → ResourceService.MAX_CONTACT_PHONE_LENGTH (50)
 *
 * If any server limit changes, change it here too.
 */
export const FIELD_LIMITS = {
  goalTitle: 200,
  goalDescription: 5000,
  targetTitle: 200,
  targetUnit: 50,
  optionText: 500,
  realityText: 500,
  checklistText: 500,
  resourceLabel: 200, // resource title / contact name / role / email
  resourceUrl: 1000,
  resourceNoteBody: 50000,
  resourcePhone: 50,
} as const;

export type FieldLimitKey = keyof typeof FIELD_LIMITS;

/**
 * Returns a clear, field-specific error message when `value` exceeds `max`, else null.
 * The message names the field and the exact limit so the user can fix it, e.g.
 * "Target title must be 200 characters or fewer (you have 214)."
 */
export function lengthError(
  value: string,
  max: number,
  label: string,
): string | null {
  const len = value.length;
  if (len <= max) return null;
  return `${label} must be ${max} characters or fewer (you have ${len}).`;
}
