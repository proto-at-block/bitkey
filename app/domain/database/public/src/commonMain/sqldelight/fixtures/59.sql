-- Test fixture data for bip177 coachmark eligibility.
-- Value 0 (ineligible) is a default test state - production eligibility is determined at runtime.
-- Keep rowId fixed at 0 to align with DAO queries.
INSERT INTO bip177CoachmarkEligibilityEntity(rowId, eligible)
VALUES (0, 0);
