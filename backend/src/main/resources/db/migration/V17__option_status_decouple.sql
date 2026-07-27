-- The option "active" radio now lives solely in the `selected` boolean; `status` holds only the
-- thumb lean ('none' | 'good_idea' | 'didnt_work'), independent of `selected`.
-- Any legacy row with status='active' already carries selected=true, so no active-state is lost.
UPDATE option SET status = 'none' WHERE status = 'active';
