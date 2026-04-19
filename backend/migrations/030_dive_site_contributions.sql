-- Dive site user contributions: corrections / new sites (moderation by ADMIN or SUPER_ADMIN).

CREATE TABLE IF NOT EXISTS dive_site_contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contribution_type VARCHAR(32) NOT NULL CHECK (contribution_type IN ('correction', 'new_site')),
    dive_site_id UUID REFERENCES dive_sites(id) ON DELETE SET NULL,
    submitter_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    proposed_data JSONB NOT NULL DEFAULT '{}',
    message TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dive_site_contributions_status ON dive_site_contributions(status);
CREATE INDEX IF NOT EXISTS idx_dive_site_contributions_submitter ON dive_site_contributions(submitter_user_id);
CREATE INDEX IF NOT EXISTS idx_dive_site_contributions_created ON dive_site_contributions(created_at DESC);

CREATE OR REPLACE FUNCTION update_dive_site_contribution_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS dive_site_contribution_updated_at_trigger ON dive_site_contributions;
CREATE TRIGGER dive_site_contribution_updated_at_trigger
    BEFORE UPDATE ON dive_site_contributions
    FOR EACH ROW
    EXECUTE FUNCTION update_dive_site_contribution_updated_at();
