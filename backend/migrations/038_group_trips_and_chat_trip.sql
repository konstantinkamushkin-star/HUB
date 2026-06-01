-- Group trips with linked TRIP_GROUP chat

ALTER TABLE chat_conversations
  ADD COLUMN IF NOT EXISTS title VARCHAR(255),
  ADD COLUMN IF NOT EXISTS "tripId" UUID;

CREATE TABLE IF NOT EXISTS group_trips (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(255) NOT NULL,
  description TEXT,
  destination VARCHAR(255),
  "organizerId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  "chatConversationId" UUID REFERENCES chat_conversations(id) ON DELETE SET NULL,
  "startDate" DATE,
  "endDate" DATE,
  "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_group_trips_organizer ON group_trips("organizerId");

CREATE TABLE IF NOT EXISTS group_trip_members (
  "tripId" UUID NOT NULL REFERENCES group_trips(id) ON DELETE CASCADE,
  "userId" UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role VARCHAR(20) NOT NULL DEFAULT 'member',
  "joinedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY ("tripId", "userId")
);

CREATE INDEX IF NOT EXISTS idx_group_trip_members_user ON group_trip_members("userId");
