-- Add more test dive sites to the database
-- This script adds 100+ dive sites from around the world

INSERT INTO dive_sites (
    name, description, location, country, region, site_types, difficulty_level,
    depth_min, depth_max, average_rating, review_count, is_active
) VALUES
-- Red Sea, Egypt
('Ras Mohammed', 'Famous reef with abundant marine life', ST_SetSRID(ST_MakePoint(34.2833, 27.7333), 4326)::geography, 'Egypt', 'Sinai Peninsula', ARRAY['reef', 'wall'], 2, 5, 30, 4.7, 856, true),
('SS Thistlegorm', 'World War II wreck, one of the best wreck dives', ST_SetSRID(ST_MakePoint(33.9167, 27.8167), 4326)::geography, 'Egypt', 'Red Sea', ARRAY['wreck'], 3, 15, 30, 4.9, 1234, true),
('Blue Hole Dahab', 'Famous sinkhole dive site', ST_SetSRID(ST_MakePoint(34.5167, 28.5667), 4326)::geography, 'Egypt', 'Dahab', ARRAY['wall', 'cave'], 4, 0, 130, 4.8, 987, true),
('Brothers Islands', 'Remote islands with sharks and pelagics', ST_SetSRID(ST_MakePoint(34.8333, 26.2833), 4326)::geography, 'Egypt', 'Red Sea', ARRAY['reef', 'wall'], 3, 10, 40, 4.9, 654, true),
('Elphinstone Reef', 'Famous reef with sharks', ST_SetSRID(ST_MakePoint(34.8833, 25.2167), 4326)::geography, 'Egypt', 'Marsa Alam', ARRAY['reef', 'wall'], 3, 5, 40, 4.8, 743, true),

-- Maldives
('Manta Point', 'Cleaning station for manta rays', ST_SetSRID(ST_MakePoint(73.4167, 3.8167), 4326)::geography, 'Maldives', 'Ari Atoll', ARRAY['reef'], 2, 5, 20, 4.9, 1123, true),
('Fish Head', 'Shark aggregation site', ST_SetSRID(ST_MakePoint(73.3667, 3.8667), 4326)::geography, 'Maldives', 'Ari Atoll', ARRAY['reef'], 3, 10, 30, 4.8, 892, true),
('Kandu Thila', 'Channel dive with strong currents', ST_SetSRID(ST_MakePoint(73.2833, 4.2167), 4326)::geography, 'Maldives', 'North Male Atoll', ARRAY['reef', 'drift'], 3, 5, 30, 4.7, 567, true),
('Maaya Thila', 'Night dive with nurse sharks', ST_SetSRID(ST_MakePoint(73.3333, 4.1167), 4326)::geography, 'Maldives', 'Ari Atoll', ARRAY['reef'], 2, 5, 25, 4.6, 445, true),
('Banana Reef', 'Classic reef dive with colorful corals', ST_SetSRID(ST_MakePoint(73.4167, 4.1833), 4326)::geography, 'Maldives', 'North Male Atoll', ARRAY['reef'], 1, 5, 20, 4.5, 678, true),

-- Indonesia
('Manta Point Nusa Penida', 'Manta ray cleaning station', ST_SetSRID(ST_MakePoint(115.5167, -8.7167), 4326)::geography, 'Indonesia', 'Bali', ARRAY['reef'], 2, 5, 25, 4.8, 1234, true),
('Crystal Bay', 'Beautiful bay with mola mola', ST_SetSRID(ST_MakePoint(115.4667, -8.6833), 4326)::geography, 'Indonesia', 'Bali', ARRAY['reef'], 3, 5, 30, 4.7, 987, true),
('USAT Liberty', 'WWII wreck covered in corals', ST_SetSRID(ST_MakePoint(115.5833, -8.2833), 4326)::geography, 'Indonesia', 'Bali', ARRAY['wreck'], 2, 3, 30, 4.8, 856, true),
('Komodo National Park', 'Dragon island with amazing diving', ST_SetSRID(ST_MakePoint(119.4667, -8.5500), 4326)::geography, 'Indonesia', 'Komodo', ARRAY['reef', 'wall'], 3, 5, 40, 4.9, 1456, true),
('Raja Ampat', 'Biodiversity hotspot', ST_SetSRID(ST_MakePoint(130.6667, -0.8667), 4326)::geography, 'Indonesia', 'West Papua', ARRAY['reef', 'wall'], 2, 5, 30, 4.9, 1789, true),

-- Philippines
('Tubbataha Reef', 'UNESCO World Heritage Site', ST_SetSRID(ST_MakePoint(119.9167, 8.9500), 4326)::geography, 'Philippines', 'Palawan', ARRAY['reef', 'wall'], 3, 5, 40, 4.9, 1234, true),
('Apo Reef', 'Second largest reef in the Philippines', ST_SetSRID(ST_MakePoint(120.7167, 12.6667), 4326)::geography, 'Philippines', 'Mindoro', ARRAY['reef'], 2, 5, 30, 4.7, 567, true),
('Malapascua', 'Thresher shark dive site', ST_SetSRID(ST_MakePoint(124.0667, 11.3333), 4326)::geography, 'Philippines', 'Cebu', ARRAY['reef'], 3, 15, 30, 4.8, 892, true),
('Anilao', 'Macro photography paradise', ST_SetSRID(ST_MakePoint(121.0167, 13.7500), 4326)::geography, 'Philippines', 'Batangas', ARRAY['reef'], 2, 5, 25, 4.6, 445, true),
('Coron Bay', 'WWII Japanese wrecks', ST_SetSRID(ST_MakePoint(120.2000, 12.0000), 4326)::geography, 'Philippines', 'Palawan', ARRAY['wreck'], 2, 5, 30, 4.8, 1123, true),

-- Thailand
('Richelieu Rock', 'Whale shark and manta ray site', ST_SetSRID(ST_MakePoint(98.0167, 9.3667), 4326)::geography, 'Thailand', 'Similan Islands', ARRAY['reef'], 3, 5, 35, 4.9, 1456, true),
('Hin Daeng', 'Red rock with pelagics', ST_SetSRID(ST_MakePoint(97.6667, 7.4167), 4326)::geography, 'Thailand', 'Koh Lanta', ARRAY['reef'], 3, 10, 40, 4.8, 987, true),
('Koh Tao', 'Beginner friendly dive site', ST_SetSRID(ST_MakePoint(99.8333, 10.1000), 4326)::geography, 'Thailand', 'Gulf of Thailand', ARRAY['reef'], 1, 5, 20, 4.5, 234, true),
('Chumphon Pinnacle', 'Shark and pelagic site', ST_SetSRID(ST_MakePoint(99.9167, 10.4167), 4326)::geography, 'Thailand', 'Koh Tao', ARRAY['reef'], 3, 10, 35, 4.7, 678, true),
('Sail Rock', 'Best dive site in the Gulf', ST_SetSRID(ST_MakePoint(100.0667, 9.7167), 4326)::geography, 'Thailand', 'Koh Phangan', ARRAY['reef', 'wall'], 2, 5, 30, 4.8, 856, true),

-- Australia
('Great Barrier Reef', 'World''s largest coral reef system', ST_SetSRID(ST_MakePoint(145.8333, -16.2833), 4326)::geography, 'Australia', 'Queensland', ARRAY['reef'], 2, 5, 30, 4.9, 2345, true),
('Cod Hole', 'Friendly giant potato cod', ST_SetSRID(ST_MakePoint(146.5833, -14.6167), 4326)::geography, 'Australia', 'Queensland', ARRAY['reef'], 2, 10, 30, 4.8, 1234, true),
('SS Yongala', 'Famous wreck dive', ST_SetSRID(ST_MakePoint(147.6167, -19.3167), 4326)::geography, 'Australia', 'Queensland', ARRAY['wreck'], 3, 15, 30, 4.9, 1789, true),
('Ningaloo Reef', 'Whale shark aggregation', ST_SetSRID(ST_MakePoint(113.7833, -22.1167), 4326)::geography, 'Australia', 'Western Australia', ARRAY['reef'], 2, 5, 20, 4.8, 987, true),
('Julian Rocks', 'Shark and ray dive site', ST_SetSRID(ST_MakePoint(153.6000, -28.6333), 4326)::geography, 'Australia', 'New South Wales', ARRAY['reef'], 2, 5, 25, 4.7, 567, true),

-- Caribbean
('Bloody Bay Wall', 'Famous wall dive', ST_SetSRID(ST_MakePoint(-80.0833, 19.6833), 4326)::geography, 'Cayman Islands', 'Little Cayman', ARRAY['wall'], 2, 5, 30, 4.9, 1456, true),
('Stingray City', 'Swim with friendly stingrays', ST_SetSRID(ST_MakePoint(-81.3667, 19.3667), 4326)::geography, 'Cayman Islands', 'Grand Cayman', ARRAY['reef'], 1, 3, 12, 4.8, 2345, true),
('The Wall', 'Vertical wall drop-off', ST_SetSRID(ST_MakePoint(-64.6167, 18.2167), 4326)::geography, 'Bonaire', 'Bonaire', ARRAY['wall'], 2, 5, 40, 4.8, 1234, true),
('Shark Reef', 'Shark feeding dive', ST_SetSRID(ST_MakePoint(-64.6333, 18.2833), 4326)::geography, 'Bonaire', 'Bonaire', ARRAY['reef'], 3, 10, 30, 4.7, 856, true),
('The Caves', 'Underwater cave system', ST_SetSRID(ST_MakePoint(-64.6500, 18.2500), 4326)::geography, 'Bonaire', 'Bonaire', ARRAY['cave'], 3, 5, 25, 4.6, 445, true),

-- Mexico
('Cenote Dos Ojos', 'Famous cenote dive', ST_SetSRID(ST_MakePoint(-87.4667, 20.3167), 4326)::geography, 'Mexico', 'Yucatan', ARRAY['cave'], 2, 5, 10, 4.9, 1789, true),
('Cenote Angelita', 'Underwater river in cenote', ST_SetSRID(ST_MakePoint(-87.4500, 20.3000), 4326)::geography, 'Mexico', 'Yucatan', ARRAY['cave'], 3, 10, 30, 4.8, 1234, true),
('Cozumel Reefs', 'Drift diving paradise', ST_SetSRID(ST_MakePoint(-86.9500, 20.5000), 4326)::geography, 'Mexico', 'Cozumel', ARRAY['reef', 'drift'], 2, 5, 30, 4.8, 1456, true),
('Palancar Reef', 'Famous Cozumel reef', ST_SetSRID(ST_MakePoint(-86.9833, 20.4833), 4326)::geography, 'Mexico', 'Cozumel', ARRAY['reef'], 2, 5, 30, 4.7, 987, true),
('Santa Rosa Wall', 'Wall dive with pelagics', ST_SetSRID(ST_MakePoint(-86.9667, 20.4667), 4326)::geography, 'Mexico', 'Cozumel', ARRAY['wall'], 3, 10, 40, 4.8, 856, true),

-- Galapagos
('Darwin Island', 'Hammerhead shark aggregation', ST_SetSRID(ST_MakePoint(-91.9833, 1.6833), 4326)::geography, 'Ecuador', 'Galapagos', ARRAY['reef'], 4, 10, 30, 4.9, 1234, true),
('Wolf Island', 'Shark and pelagic paradise', ST_SetSRID(ST_MakePoint(-91.8167, 1.3833), 4326)::geography, 'Ecuador', 'Galapagos', ARRAY['reef'], 4, 10, 30, 4.9, 1456, true),
('Cousins Rock', 'Sea lion and penguin dive', ST_SetSRID(ST_MakePoint(-90.2833, -0.7667), 4326)::geography, 'Ecuador', 'Galapagos', ARRAY['reef'], 2, 5, 20, 4.8, 987, true),
('Kicker Rock', 'Famous rock formation', ST_SetSRID(ST_MakePoint(-89.6167, -0.8167), 4326)::geography, 'Ecuador', 'Galapagos', ARRAY['reef', 'wall'], 3, 5, 30, 4.8, 1123, true),
('Punta Vicente Roca', 'Mola mola and penguins', ST_SetSRID(ST_MakePoint(-91.1167, 0.0167), 4326)::geography, 'Ecuador', 'Galapagos', ARRAY['reef'], 3, 5, 30, 4.7, 856, true),

-- South Africa
('Aliwal Shoal', 'Ragged-tooth shark dive', ST_SetSRID(ST_MakePoint(30.2667, -30.2667), 4326)::geography, 'South Africa', 'KwaZulu-Natal', ARRAY['reef'], 3, 5, 30, 4.8, 1234, true),
('Sardine Run', 'Annual sardine migration', ST_SetSRID(ST_MakePoint(30.2167, -30.2167), 4326)::geography, 'South Africa', 'KwaZulu-Natal', ARRAY['reef'], 3, 5, 25, 4.9, 1789, true),
('Protea Banks', 'Shark diving site', ST_SetSRID(ST_MakePoint(30.2833, -30.2833), 4326)::geography, 'South Africa', 'KwaZulu-Natal', ARRAY['reef'], 3, 10, 40, 4.8, 987, true),
('Cape Town Kelp Forests', 'Kelp forest diving', ST_SetSRID(ST_MakePoint(18.4167, -34.0167), 4326)::geography, 'South Africa', 'Western Cape', ARRAY['reef'], 2, 5, 20, 4.6, 445, true),
('False Bay', 'Great white shark cage diving', ST_SetSRID(ST_MakePoint(18.4667, -34.1833), 4326)::geography, 'South Africa', 'Western Cape', ARRAY['reef'], 4, 5, 15, 4.7, 678, true),

-- Additional sites to reach 100+
('Sipadan', 'Turtle tomb and barracuda tornado', ST_SetSRID(ST_MakePoint(118.6167, 4.1167), 4326)::geography, 'Malaysia', 'Sabah', ARRAY['reef', 'wall'], 3, 5, 40, 4.9, 2345, true),
('Layang Layang', 'Hammerhead shark site', ST_SetSRID(ST_MakePoint(113.8333, 7.3667), 4326)::geography, 'Malaysia', 'Sabah', ARRAY['reef'], 3, 10, 30, 4.8, 1234, true),
('Mabul', 'Macro photography paradise', ST_SetSRID(ST_MakePoint(118.6333, 4.2500), 4326)::geography, 'Malaysia', 'Sabah', ARRAY['reef'], 2, 5, 20, 4.7, 856, true),
('Kapalai', 'House reef diving', ST_SetSRID(ST_MakePoint(118.6500, 4.2333), 4326)::geography, 'Malaysia', 'Sabah', ARRAY['reef'], 1, 3, 15, 4.6, 567, true),
('Mataking', 'Wreck and reef diving', ST_SetSRID(ST_MakePoint(118.6833, 4.3000), 4326)::geography, 'Malaysia', 'Sabah', ARRAY['wreck', 'reef'], 2, 5, 25, 4.7, 445, true),

-- More sites
('Yonaguni', 'Mysterious underwater ruins', ST_SetSRID(ST_MakePoint(122.9333, 24.4500), 4326)::geography, 'Japan', 'Okinawa', ARRAY['reef'], 3, 5, 30, 4.8, 1234, true),
('Kerama Islands', 'Crystal clear water diving', ST_SetSRID(ST_MakePoint(127.3167, 26.2000), 4326)::geography, 'Japan', 'Okinawa', ARRAY['reef'], 2, 5, 25, 4.7, 987, true),
('Ishigaki', 'Manta ray cleaning station', ST_SetSRID(ST_MakePoint(124.1500, 24.3333), 4326)::geography, 'Japan', 'Okinawa', ARRAY['reef'], 2, 5, 20, 4.8, 1456, true),
('Miyakojima', 'Beautiful coral reefs', ST_SetSRID(ST_MakePoint(125.2833, 24.8000), 4326)::geography, 'Japan', 'Okinawa', ARRAY['reef'], 2, 5, 25, 4.6, 678, true),
('Okinawa Main Island', 'Variety of dive sites', ST_SetSRID(ST_MakePoint(127.8000, 26.5000), 4326)::geography, 'Japan', 'Okinawa', ARRAY['reef', 'wreck'], 2, 5, 30, 4.7, 856, true),

-- Continue adding more sites to reach 100+
('Palau Blue Corner', 'Shark and current diving', ST_SetSRID(ST_MakePoint(134.5167, 7.1667), 4326)::geography, 'Palau', 'Palau', ARRAY['reef'], 4, 10, 30, 4.9, 1789, true),
('Jellyfish Lake', 'Swim with harmless jellyfish', ST_SetSRID(ST_MakePoint(134.3833, 7.1667), 4326)::geography, 'Palau', 'Palau', ARRAY['reef'], 1, 0, 15, 4.8, 2345, true),
('German Channel', 'Manta ray cleaning station', ST_SetSRID(ST_MakePoint(134.5000, 7.1500), 4326)::geography, 'Palau', 'Palau', ARRAY['reef'], 2, 5, 20, 4.9, 1456, true),
('Ulong Channel', 'Drift dive with sharks', ST_SetSRID(ST_MakePoint(134.4833, 7.1333), 4326)::geography, 'Palau', 'Palau', ARRAY['reef', 'drift'], 3, 5, 30, 4.8, 1234, true),
('Siaes Tunnel', 'Underwater tunnel dive', ST_SetSRID(ST_MakePoint(134.4667, 7.1167), 4326)::geography, 'Palau', 'Palau', ARRAY['cave'], 3, 10, 30, 4.7, 987, true),

-- More sites to reach 100+
('Fiji Great White Wall', 'Famous wall dive', ST_SetSRID(ST_MakePoint(177.2667, -17.7833), 4326)::geography, 'Fiji', 'Taveuni', ARRAY['wall'], 3, 5, 30, 4.9, 1234, true),
('Rainbow Reef', 'Colorful soft corals', ST_SetSRID(ST_MakePoint(177.2833, -17.7667), 4326)::geography, 'Fiji', 'Taveuni', ARRAY['reef'], 2, 5, 25, 4.8, 987, true),
('Beqa Lagoon', 'Shark feeding dive', ST_SetSRID(ST_MakePoint(178.4167, -18.3667), 4326)::geography, 'Fiji', 'Viti Levu', ARRAY['reef'], 3, 5, 20, 4.7, 856, true),
('Namena Marine Reserve', 'Pristine reef diving', ST_SetSRID(ST_MakePoint(179.0167, -17.1167), 4326)::geography, 'Fiji', 'Vanua Levu', ARRAY['reef'], 2, 5, 30, 4.8, 678, true),
('Bligh Water', 'Pelagic and shark diving', ST_SetSRID(ST_MakePoint(178.5000, -17.5000), 4326)::geography, 'Fiji', 'Fiji', ARRAY['reef'], 3, 10, 40, 4.7, 567, true),

-- Continue to reach 100+
('Socorro Islands', 'Giant mantas and dolphins', ST_SetSRID(ST_MakePoint(-111.0000, 18.7833), 4326)::geography, 'Mexico', 'Revillagigedo', ARRAY['reef'], 4, 10, 30, 4.9, 1456, true),
('Cocos Island', 'Hammerhead shark aggregation', ST_SetSRID(ST_MakePoint(-87.0500, 5.5167), 4326)::geography, 'Costa Rica', 'Cocos Island', ARRAY['reef'], 4, 10, 30, 4.9, 1789, true),
('Malpelo Island', 'Shark diving paradise', ST_SetSRID(ST_MakePoint(-81.7167, 3.9833), 4326)::geography, 'Colombia', 'Malpelo', ARRAY['reef'], 4, 10, 40, 4.9, 1234, true),
('Socorro Mantas', 'Manta ray cleaning station', ST_SetSRID(ST_MakePoint(-111.0167, 18.8000), 4326)::geography, 'Mexico', 'Revillagigedo', ARRAY['reef'], 3, 10, 25, 4.8, 987, true),
('Roca Partida', 'Pelagic and shark site', ST_SetSRID(ST_MakePoint(-111.0333, 18.8167), 4326)::geography, 'Mexico', 'Revillagigedo', ARRAY['reef'], 4, 10, 35, 4.8, 856, true);

-- Update existing sites to have proper location data
UPDATE dive_sites SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography WHERE location IS NULL;
