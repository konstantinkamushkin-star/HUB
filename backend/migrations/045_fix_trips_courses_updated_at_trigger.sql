-- trips / courses use snake_case updated_at; shared update_updated_at_column()
-- assigns NEW."updatedAt" (camelCase) and breaks UPDATE on these tables:
--   record "new" has no field "updatedAt"
-- Fix: dedicated snake_case trigger function for trips + courses.

CREATE OR REPLACE FUNCTION update_updated_at_snake_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_trips_updated_at ON trips;
CREATE TRIGGER update_trips_updated_at
    BEFORE UPDATE ON trips
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_snake_column();

DROP TRIGGER IF EXISTS update_courses_updated_at ON courses;
CREATE TRIGGER update_courses_updated_at
    BEFORE UPDATE ON courses
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_snake_column();
