-- friendships uses quoted "updatedAt"; DB function update_updated_at_column() may use
-- NEW.updated_at (snake_case), which does not exist on this table → UPDATE ... 500.
DROP TRIGGER IF EXISTS update_friendships_updated_at ON friendships;
