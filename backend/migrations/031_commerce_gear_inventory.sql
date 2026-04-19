-- Shop catalog / orders (mobile Sell tab parity), center gear, center inventory (MVP).

CREATE TABLE IF NOT EXISTS shop_products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    stock INT NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'active',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_products_shop ON shop_products(shop_id);

CREATE TABLE IF NOT EXISTS shop_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    customer_name TEXT NOT NULL,
    item_count INT NOT NULL DEFAULT 1,
    total NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'new',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_orders_shop ON shop_orders(shop_id);

CREATE TABLE IF NOT EXISTS center_gear_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dive_center_id UUID NOT NULL REFERENCES dive_centers(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'other',
    manufacturer TEXT,
    status TEXT NOT NULL DEFAULT 'available',
    "condition" TEXT NOT NULL DEFAULT 'good',
    payload JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_center_gear_center ON center_gear_items(dive_center_id);

CREATE TABLE IF NOT EXISTS center_inventory_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dive_center_id UUID NOT NULL REFERENCES dive_centers(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'other',
    status TEXT NOT NULL DEFAULT 'available',
    "condition" TEXT NOT NULL DEFAULT 'good',
    location TEXT,
    size TEXT,
    notes TEXT,
    issued_to_name TEXT,
    due_at TEXT,
    checkout_notes TEXT,
    checkout_handed_off_by TEXT,
    checkout_handed_off_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_center_inv_center ON center_inventory_items(dive_center_id);

CREATE TABLE IF NOT EXISTS center_inventory_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dive_center_id UUID NOT NULL REFERENCES dive_centers(id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES center_inventory_items(id) ON DELETE CASCADE,
    item_name TEXT NOT NULL,
    title TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'open',
    priority TEXT NOT NULL DEFAULT 'medium',
    description TEXT,
    checklist JSONB DEFAULT '[]'::jsonb,
    signed_by TEXT,
    signed_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    events JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_center_inv_tickets_center ON center_inventory_tickets(dive_center_id);
CREATE INDEX IF NOT EXISTS idx_center_inv_tickets_item ON center_inventory_tickets(item_id);
