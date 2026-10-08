-- Rates now record the f8e environment that produced them, so the fixture carries
-- provenance. Migration 87 drops pre-existing rows (unknown origin), so this row
-- represents a rate written after that column existed.
INSERT INTO exchangeRateEntity(fromCurrency, toCurrency, rate, timeRetrieved, f8eEnvironment)
VALUES ('USD', 'BTC', 0.000041, 1706730439000, 'Production');
