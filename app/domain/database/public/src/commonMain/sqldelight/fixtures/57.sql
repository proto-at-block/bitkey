-- Insert deprecated coachmarks to test that migration 57 deletes them
INSERT INTO coachmarkEntity(id, viewed, expiration) VALUES ('InheritanceCoachmark', 1, 1735689600000);
INSERT INTO coachmarkEntity(id, viewed, expiration) VALUES ('BalanceGraphCoachmark', 1, 1735689600000);
INSERT INTO coachmarkEntity(id, viewed, expiration) VALUES ('SecurityHubHomeCoachmark', 1, 1735689600000);
