// Script to delete fake/generated dive sites from the database
// Keeps only real dive sites from the known list
const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');

// Database connection configuration
const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
};

const pool = new Pool(dbConfig);

// Real dive sites from import_dive_sites_worldwide.js
// This is the list of known real dive sites
const realDiveSites = [
  // RED SEA - Egypt
  { name: "SS Thistlegorm", lat: 27.8167, lng: 33.9167 },
  { name: "Ras Mohammed", lat: 27.7333, lng: 34.2500 },
  { name: "Blue Hole Dahab", lat: 28.5667, lng: 34.5167 },
  { name: "Brothers Islands", lat: 26.3000, lng: 34.8500 },
  { name: "Elphinstone Reef", lat: 25.1833, lng: 34.8833 },
  { name: "Abu Nuhas", lat: 27.6167, lng: 33.9167 },
  { name: "Dunraven Wreck", lat: 27.7167, lng: 34.2167 },
  { name: "Shaab El Erg", lat: 27.6833, lng: 33.9500 },
  { name: "Gubal Island", lat: 27.6500, lng: 33.8500 },
  { name: "Straits of Tiran", lat: 28.0000, lng: 34.4667 },
  
  // MALDIVES
  { name: "Manta Point", lat: 3.9167, lng: 73.4167 },
  { name: "Maaya Thila", lat: 3.8833, lng: 73.4500 },
  { name: "Kandolhu Thila", lat: 3.8500, lng: 73.4833 },
  { name: "Hammerhead Point", lat: 4.2500, lng: 72.9167 },
  { name: "Banana Reef", lat: 4.1833, lng: 73.4167 },
  { name: "Lion's Head", lat: 4.1500, lng: 73.4500 },
  { name: "Kuda Haa", lat: 4.1167, lng: 73.4833 },
  { name: "Fesdu Wreck", lat: 4.0833, lng: 73.5167 },
  { name: "HP Reef", lat: 4.0500, lng: 73.5500 },
  { name: "Rasdhoo Atoll", lat: 4.0167, lng: 72.9833 },
  
  // INDONESIA
  { name: "USAT Liberty", lat: -8.2833, lng: 115.5833 },
  { name: "Crystal Bay", lat: -8.7167, lng: 115.4500 },
  { name: "Manta Point Nusa Penida", lat: -8.7333, lng: 115.4667 },
  { name: "Tulamben Drop Off", lat: -8.2833, lng: 115.5833 },
  { name: "Secret Bay", lat: -8.2500, lng: 115.6000 },
  { name: "Komodo National Park", lat: -8.5500, lng: 119.4500 },
  { name: "Batu Bolong", lat: -8.5667, lng: 119.4667 },
  { name: "Cannibal Rock", lat: -8.5833, lng: 119.4833 },
  { name: "Manta Alley", lat: -8.6000, lng: 119.5000 },
  { name: "Raja Ampat - Cape Kri", lat: -0.5667, lng: 130.6833 },
  { name: "Raja Ampat - Blue Magic", lat: -0.5500, lng: 130.7000 },
  { name: "Raja Ampat - Manta Sandy", lat: -0.5333, lng: 130.7167 },
  { name: "Raja Ampat - The Passage", lat: -0.5167, lng: 130.7333 },
  
  // PHILIPPINES
  { name: "Tubbataha Reef", lat: 8.9500, lng: 119.8667 },
  { name: "Apo Reef", lat: 12.6667, lng: 120.4167 },
  { name: "Anilao", lat: 13.7500, lng: 121.0167 },
  { name: "Puerto Galera", lat: 13.5000, lng: 120.9500 },
  { name: "Malapascua", lat: 11.3333, lng: 124.1167 },
  { name: "Moalboal", lat: 9.9500, lng: 123.4000 },
  { name: "Coron Bay", lat: 12.0000, lng: 120.2000 },
  { name: "Apo Island", lat: 9.0833, lng: 123.2667 },
  { name: "Dauin", lat: 9.2000, lng: 123.2667 },
  { name: "Bohol - Balicasag", lat: 9.5167, lng: 123.6833 },
  
  // CARIBBEAN
  { name: "Blue Hole Belize", lat: 17.3167, lng: -87.5333 },
  { name: "Great Blue Hole", lat: 17.3167, lng: -87.5333 },
  { name: "Turneffe Atoll", lat: 17.3000, lng: -87.8000 },
  { name: "Cozumel - Palancar Reef", lat: 20.2833, lng: -87.0167 },
  { name: "Cozumel - Santa Rosa Wall", lat: 20.3000, lng: -87.0333 },
  { name: "Cozumel - Columbia Wall", lat: 20.3167, lng: -87.0500 },
  { name: "Roatan - West End Wall", lat: 16.3167, lng: -86.5500 },
  { name: "Roatan - Mary's Place", lat: 16.3333, lng: -86.5667 },
  { name: "Bonaire - 1000 Steps", lat: 12.2167, lng: -68.3833 },
  { name: "Bonaire - Hilma Hooker", lat: 12.2000, lng: -68.4000 },
  { name: "Curacao - Superior Producer", lat: 12.1167, lng: -68.9500 },
  { name: "Aruba - Antilla Wreck", lat: 12.5667, lng: -70.0500 },
  { name: "Grand Cayman - Stingray City", lat: 19.3667, lng: -81.3833 },
  { name: "Grand Cayman - Bloody Bay Wall", lat: 19.7167, lng: -79.9500 },
  { name: "Saba - Tent Reef", lat: 17.6333, lng: -63.2333 },
  
  // GREAT BARRIER REEF
  { name: "Cod Hole", lat: -14.6167, lng: 145.6167 },
  { name: "Ribbon Reefs", lat: -14.5000, lng: 145.5000 },
  { name: "Osprey Reef", lat: -13.8667, lng: 146.5833 },
  { name: "Heron Island", lat: -23.4333, lng: 151.9167 },
  { name: "Lady Elliot Island", lat: -24.1167, lng: 152.7167 },
  { name: "Agincourt Reef", lat: -16.0500, lng: 145.8500 },
  { name: "Norman Reef", lat: -16.0833, lng: 145.8833 },
  { name: "Saxon Reef", lat: -16.1167, lng: 145.9167 },
  
  // GALAPAGOS
  { name: "Darwin's Arch", lat: 1.6667, lng: -91.9833 },
  { name: "Wolf Island", lat: 1.3833, lng: -91.8167 },
  { name: "Cousins Rock", lat: -0.7667, lng: -90.3167 },
  { name: "Kicker Rock", lat: -0.7833, lng: -89.4833 },
  { name: "Punta Vicente Roca", lat: 0.0167, lng: -91.5500 },
  { name: "Gordon Rocks", lat: -0.7167, lng: -90.2833 },
  
  // COCOS ISLAND
  { name: "Manuelita", lat: 5.5333, lng: -87.0667 },
  { name: "Dirty Rock", lat: 5.5500, lng: -87.0833 },
  { name: "Alcyone", lat: 5.5167, lng: -87.0500 },
  { name: "Bajo Alcyone", lat: 5.5000, lng: -87.0333 },
  
  // PALAU
  { name: "Blue Corner", lat: 7.1667, lng: 134.2500 },
  { name: "German Channel", lat: 7.1833, lng: 134.2667 },
  { name: "Ulong Channel", lat: 7.2000, lng: 134.2833 },
  { name: "Jellyfish Lake", lat: 7.1667, lng: 134.3833 },
  { name: "Siaes Tunnel", lat: 7.1500, lng: 134.3000 },
  { name: "New Drop Off", lat: 7.1333, lng: 134.3167 },
  
  // MICRONESIA
  { name: "Truk Lagoon - Fujikawa Maru", lat: 7.4167, lng: 151.8833 },
  { name: "Truk Lagoon - Shinkoku Maru", lat: 7.4333, lng: 151.9000 },
  { name: "Truk Lagoon - San Francisco Maru", lat: 7.4500, lng: 151.9167 },
  { name: "Yap - Manta Ray Bay", lat: 9.5167, lng: 138.1167 },
  { name: "Yap - Vertigo", lat: 9.5333, lng: 138.1333 },
  
  // SOUTH AFRICA
  { name: "Aliwal Shoal", lat: -30.2500, lng: 30.8167 },
  { name: "Sodwana Bay", lat: -27.5333, lng: 32.6833 },
  { name: "Protea Banks", lat: -30.8333, lng: 30.4167 },
  
  // MEDITERRANEAN
  { name: "Calanques de Marseille", lat: 43.2167, lng: 5.3667 },
  { name: "Portofino", lat: 44.3000, lng: 9.2167 },
  { name: "Capo Caccia", lat: 40.5667, lng: 8.1667 },
  { name: "Malta - Blue Hole", lat: 36.0500, lng: 14.1833 },
  { name: "Malta - Um El Faroud", lat: 35.8833, lng: 14.5167 },
  { name: "Croatia - Vis Island", lat: 43.0500, lng: 16.1833 },
  { name: "Greece - Zakynthos", lat: 37.7833, lng: 20.9000 },
  { name: "Spain - Cabo de Palos", lat: 37.6333, lng: -0.6833 },
  
  // JAPAN
  { name: "Yonaguni Monument", lat: 24.4333, lng: 123.0167 },
  { name: "Kerama Islands", lat: 26.2000, lng: 127.3000 },
  { name: "Izu Peninsula", lat: 34.7500, lng: 139.0833 },
  { name: "Ogasawara Islands", lat: 27.0833, lng: 142.2167 },
  
  // THAILAND
  { name: "Similan Islands - Richelieu Rock", lat: 8.6333, lng: 97.6500 },
  { name: "Similan Islands - Elephant Head Rock", lat: 8.6500, lng: 97.6667 },
  { name: "Koh Tao - Chumphon Pinnacle", lat: 10.0833, lng: 99.8167 },
  { name: "Koh Tao - Sail Rock", lat: 10.1000, lng: 99.8333 },
  { name: "Koh Phi Phi - Maya Bay", lat: 7.6667, lng: 98.7667 },
  { name: "Koh Lanta - Hin Daeng", lat: 7.3833, lng: 99.0167 },
  { name: "Koh Lanta - Hin Muang", lat: 7.4000, lng: 99.0333 },
  
  // MALAYSIA
  { name: "Sipadan - Barracuda Point", lat: 4.1167, lng: 118.6167 },
  { name: "Sipadan - Drop Off", lat: 4.1333, lng: 118.6333 },
  { name: "Sipadan - South Point", lat: 4.1000, lng: 118.6000 },
  { name: "Layang Layang", lat: 7.3667, lng: 113.8333 },
  { name: "Tioman Island", lat: 2.7833, lng: 104.1833 },
  { name: "Perhentian Islands", lat: 5.9167, lng: 102.7333 },
  
  // SRI LANKA
  { name: "Great Basses Reef", lat: 6.1833, lng: 81.5167 },
  { name: "Bar Reef", lat: 8.4500, lng: 79.7833 },
  
  // SEYCHELLES
  { name: "Aldabra Atoll", lat: -9.4167, lng: 46.4167 },
  { name: "Mahe - Brissare Rocks", lat: -4.6167, lng: 55.5167 },
  { name: "Praslin - Coco Island", lat: -4.3333, lng: 55.7333 },
  
  // MOZAMBIQUE
  { name: "Tofo Beach", lat: -23.8500, lng: 35.5500 },
  { name: "Ponta do Ouro", lat: -26.8500, lng: 32.8667 },
  
  // MADAGASCAR
  { name: "Nosy Be", lat: -13.3167, lng: 48.2667 },
  { name: "Nosy Tanikely", lat: -13.4667, lng: 48.2333 },
  
  // FIJI
  { name: "Great Astrolabe Reef", lat: -18.9667, lng: 178.5167 },
  { name: "Beqa Lagoon", lat: -18.3833, lng: 178.1333 },
  { name: "Namena Marine Reserve", lat: -17.0833, lng: 179.1167 },
  { name: "Bligh Water", lat: -17.5000, lng: 178.0000 },
  
  // TONGA
  { name: "Vava'u", lat: -18.6500, lng: -173.9833 },
  
  // SOLOMON ISLANDS
  { name: "Uepi Island", lat: -8.4000, lng: 157.9167 },
  { name: "Munda", lat: -8.3333, lng: 157.2500 },
  
  // PAPUA NEW GUINEA
  { name: "Kimbe Bay", lat: -5.5500, lng: 150.1500 },
  { name: "Milne Bay", lat: -10.3833, lng: 150.5000 },
  { name: "Tufi", lat: -9.0833, lng: 149.3167 },
  
  // HAWAII - USA
  { name: "Molokini Crater", lat: 20.6333, lng: -156.5000 },
  { name: "Kona - Manta Ray Night Dive", lat: 19.6333, lng: -156.0000 },
  { name: "Hanauma Bay", lat: 21.2667, lng: -157.7000 },
  { name: "Shark's Cove", lat: 21.6500, lng: -158.0833 },
  
  // CALIFORNIA - USA
  { name: "Catalina Island", lat: 33.3833, lng: -118.4167 },
  { name: "Channel Islands", lat: 34.0500, lng: -119.4167 },
  { name: "Monterey Bay", lat: 36.8000, lng: -121.9000 },
  
  // FLORIDA - USA
  { name: "Key Largo - Molasses Reef", lat: 25.0167, lng: -80.3667 },
  { name: "Key Largo - Christ of the Abyss", lat: 25.0333, lng: -80.3833 },
  { name: "Key West - Vandenberg Wreck", lat: 24.4500, lng: -81.7333 },
  { name: "Key West - Dry Tortugas", lat: 24.6333, lng: -82.8667 },
  
  // CANADA
  { name: "Tobermory - Fathom Five", lat: 45.2500, lng: -81.6667 },
  { name: "Vancouver Island", lat: 49.6500, lng: -125.4500 },
  
  // BRAZIL
  { name: "Fernando de Noronha", lat: -3.8500, lng: -32.4167 },
  { name: "Abrolhos", lat: -17.9667, lng: -38.7000 },
  
  // COLOMBIA
  { name: "Malpelo Island", lat: 4.0000, lng: -81.6000 },
  { name: "Providencia", lat: 13.3500, lng: -81.3667 },
  { name: "San Andres", lat: 12.5833, lng: -81.7000 },
  
  // VENEZUELA
  { name: "Los Roques", lat: 11.8500, lng: -66.7500 },
  
  // DOMINICAN REPUBLIC
  { name: "Bayahibe", lat: 18.3667, lng: -68.8333 },
  
  // TURKS AND CAICOS
  { name: "Grand Turk - Wall", lat: 21.4667, lng: -71.1333 },
  { name: "Providenciales", lat: 21.7833, lng: -72.2833 },
  
  // BAHAMAS
  { name: "Tiger Beach", lat: 26.8000, lng: -79.2833 },
  { name: "Nassau - Stuart Cove", lat: 25.0500, lng: -77.4667 },
  { name: "Exuma Cays", lat: 24.0833, lng: -76.4167 },
  
  // ISRAEL
  { name: "Eilat - Japanese Gardens", lat: 29.5000, lng: 34.9167 },
  { name: "Eilat - Satil Wreck", lat: 29.5167, lng: 34.9333 },
  
  // JORDAN
  { name: "Aqaba - Cedar Pride", lat: 29.5167, lng: 34.9833 },
  
  // SAUDI ARABIA
  { name: "Farasan Banks", lat: 16.7000, lng: 42.1167 },
  
  // SUDAN
  { name: "Sanganeb Atoll", lat: 19.7333, lng: 37.4333 },
  { name: "Shaab Rumi", lat: 19.7167, lng: 37.4500 },
  { name: "Angarosh", lat: 19.7000, lng: 37.4667 },
  
  // ERITREA
  { name: "Dahlak Archipelago", lat: 15.8333, lng: 40.2000 },
  
  // DJIBOUTI
  { name: "Seven Brothers", lat: 12.4667, lng: 43.4167 },
  
  // Also include sites from add_test_data.sql
  { name: "Ras Mohammed", lat: 27.7333, lng: 34.2833 },
  { name: "SS Thistlegorm", lat: 27.8167, lng: 33.9167 },
  { name: "Blue Hole Dahab", lat: 28.5667, lng: 34.5167 },
  { name: "Brothers Islands", lat: 26.2833, lng: 34.8333 },
  { name: "Elphinstone Reef", lat: 25.2167, lng: 34.8833 },
  { name: "Manta Point", lat: 3.8167, lng: 73.4167 },
  { name: "Fish Head", lat: 3.8667, lng: 73.3667 },
  { name: "Kandu Thila", lat: 4.2167, lng: 73.2833 },
  { name: "Maaya Thila", lat: 4.1167, lng: 73.3333 },
  { name: "Banana Reef", lat: 4.1833, lng: 73.4167 },
  { name: "Manta Point Nusa Penida", lat: -8.7167, lng: 115.5167 },
  { name: "Crystal Bay", lat: -8.6833, lng: 115.4667 },
  { name: "USAT Liberty", lat: -8.2833, lng: 115.5833 },
  { name: "Komodo National Park", lat: -8.5500, lng: 119.4667 },
  { name: "Raja Ampat", lat: -0.8667, lng: 130.6667 },
  { name: "Tubbataha Reef", lat: 8.9500, lng: 119.9167 },
  { name: "Apo Reef", lat: 12.6667, lng: 120.7167 },
  { name: "Malapascua", lat: 11.3333, lng: 124.0667 },
  { name: "Anilao", lat: 13.7500, lng: 121.0167 },
  { name: "Coron Bay", lat: 12.0000, lng: 120.2000 },
  { name: "Richelieu Rock", lat: 9.3667, lng: 98.0167 },
  { name: "Hin Daeng", lat: 7.4167, lng: 97.6667 },
  { name: "Koh Tao", lat: 10.1000, lng: 99.8333 },
  { name: "Chumphon Pinnacle", lat: 10.4167, lng: 99.9167 },
  { name: "Sail Rock", lat: 9.7167, lng: 100.0667 },
  { name: "Great Barrier Reef", lat: -16.2833, lng: 145.8333 },
  { name: "Cod Hole", lat: -14.6167, lng: 146.5833 },
  { name: "SS Yongala", lat: -19.3167, lng: 147.6167 },
  { name: "Ningaloo Reef", lat: -22.1167, lng: 113.7833 },
  { name: "Julian Rocks", lat: -28.6333, lng: 153.6000 },
  { name: "Bloody Bay Wall", lat: 19.6833, lng: -80.0833 },
  { name: "Stingray City", lat: 19.3667, lng: -81.3667 },
  { name: "The Wall", lat: 18.2167, lng: -64.6167 },
  { name: "Shark Reef", lat: 18.2833, lng: -64.6333 },
  { name: "The Caves", lat: 18.2500, lng: -64.6500 },
  { name: "Cenote Dos Ojos", lat: 20.3167, lng: -87.4667 },
  { name: "Cenote Angelita", lat: 20.3000, lng: -87.4500 },
  { name: "Cozumel Reefs", lat: 20.5000, lng: -86.9500 },
  { name: "Palancar Reef", lat: 20.4833, lng: -86.9833 },
  { name: "Santa Rosa Wall", lat: 20.4667, lng: -86.9667 },
  { name: "Darwin Island", lat: 1.6833, lng: -91.9833 },
  { name: "Wolf Island", lat: 1.3833, lng: -91.8167 },
  { name: "Cousins Rock", lat: -0.7667, lng: -90.2833 },
  { name: "Kicker Rock", lat: -0.8167, lng: -89.6167 },
  { name: "Punta Vicente Roca", lat: 0.0167, lng: -91.1167 },
  { name: "Aliwal Shoal", lat: -30.2667, lng: 30.2667 },
  { name: "Sardine Run", lat: -30.2167, lng: 30.2167 },
  { name: "Protea Banks", lat: -30.2833, lng: 30.2833 },
  { name: "Cape Town Kelp Forests", lat: -34.0167, lng: 18.4167 },
  { name: "False Bay", lat: -34.1833, lng: 18.4667 },
  { name: "Sipadan", lat: 4.1167, lng: 118.6167 },
  { name: "Layang Layang", lat: 7.3667, lng: 113.8333 },
  { name: "Mabul", lat: 4.2500, lng: 118.6333 },
  { name: "Kapalai", lat: 4.2333, lng: 118.6500 },
  { name: "Mataking", lat: 4.3000, lng: 118.6833 },
  { name: "Yonaguni", lat: 24.4500, lng: 122.9333 },
  { name: "Kerama Islands", lat: 26.2000, lng: 127.3167 },
  { name: "Ishigaki", lat: 24.3333, lng: 124.1500 },
  { name: "Miyakojima", lat: 24.8000, lng: 125.2833 },
  { name: "Okinawa Main Island", lat: 26.5000, lng: 127.8000 },
  { name: "Palau Blue Corner", lat: 7.1667, lng: 134.5167 },
  { name: "Jellyfish Lake", lat: 7.1667, lng: 134.3833 },
  { name: "German Channel", lat: 7.1500, lng: 134.5000 },
  { name: "Ulong Channel", lat: 7.1333, lng: 134.4833 },
  { name: "Siaes Tunnel", lat: 7.1167, lng: 134.4667 },
  { name: "Fiji Great White Wall", lat: -17.7833, lng: 177.2667 },
  { name: "Rainbow Reef", lat: -17.7667, lng: 177.2833 },
  { name: "Beqa Lagoon", lat: -18.3667, lng: 178.4167 },
  { name: "Namena Marine Reserve", lat: -17.1167, lng: 179.0167 },
  { name: "Bligh Water", lat: -17.5000, lng: 178.5000 },
  { name: "Socorro Islands", lat: 18.7833, lng: -111.0000 },
  { name: "Cocos Island", lat: 5.5167, lng: -87.0500 },
  { name: "Malpelo Island", lat: 3.9833, lng: -81.7167 },
  { name: "Socorro Mantas", lat: 18.8000, lng: -111.0167 },
  { name: "Roca Partida", lat: 18.8167, lng: -111.0333 },
  
  // From migration file
  { name: "Blue Hole", lat: 17.3158, lng: -87.5346 },
  { name: "Great Blue Hole", lat: 17.3158, lng: -87.5346 },
  { name: "Shark Ray Alley", lat: 17.9167, lng: -87.9500 },
];

// Create a set of real site keys for quick lookup
// Allow some coordinate tolerance (0.1 degrees ≈ 11km)
const COORD_TOLERANCE = 0.1;

function isRealSite(name, lat, lng) {
  const normalizedName = name.trim().toLowerCase();
  
  for (const realSite of realDiveSites) {
    const realName = realSite.name.trim().toLowerCase();
    const latDiff = Math.abs(lat - realSite.lat);
    const lngDiff = Math.abs(lng - realSite.lng);
    
    // Check if name matches (allowing for slight variations)
    if (realName === normalizedName || 
        normalizedName.includes(realName) || 
        realName.includes(normalizedName)) {
      // If name matches, check coordinates are close
      if (latDiff <= COORD_TOLERANCE && lngDiff <= COORD_TOLERANCE) {
        return true;
      }
    }
  }
  
  return false;
}

async function deleteFakeDiveSites() {
  console.log('🗑️  Начинаю удаление несуществующих дайвсайтов...\n');
  
  try {
    // Test database connection
    await pool.query('SELECT 1');
    console.log('✅ Подключение к базе данных успешно\n');
  } catch (error) {
    console.error('❌ Ошибка подключения к базе данных:');
    console.error(`   ${error.message}`);
    console.error('\n💡 Убедитесь, что:');
    console.error('   1. PostgreSQL запущен');
    console.error('   2. Параметры подключения правильные');
    await pool.end();
    process.exit(1);
  }
  
  try {
    // Get all dive sites from database
    console.log('🔍 Получаю список всех дайвсайтов из базы данных...');
    const result = await pool.query(`
      SELECT id, name, latitude, longitude 
      FROM dive_sites
      ORDER BY name
    `);
    
    const allSites = result.rows;
    console.log(`   Найдено ${allSites.length} дайвсайтов в базе данных\n`);
    
    // Separate real and fake sites
    const realSites = [];
    const fakeSites = [];
    
    for (const site of allSites) {
      if (site.latitude && site.longitude) {
        if (isRealSite(site.name, site.latitude, site.longitude)) {
          realSites.push(site);
        } else {
          fakeSites.push(site);
        }
      } else {
        // Sites without coordinates are considered fake
        fakeSites.push(site);
      }
    }
    
    console.log(`✅ Реальных дайвсайтов: ${realSites.length}`);
    console.log(`❌ Несуществующих дайвсайтов: ${fakeSites.length}\n`);
    
    if (fakeSites.length === 0) {
      console.log('✅ Все дайвсайты в базе данных являются реальными!\n');
      await pool.end();
      return;
    }
    
    // Show some examples of fake sites
    console.log('📋 Примеры несуществующих дайвсайтов для удаления:');
    fakeSites.slice(0, 10).forEach(site => {
      console.log(`   - ${site.name} (${site.latitude}, ${site.longitude})`);
    });
    if (fakeSites.length > 10) {
      console.log(`   ... и еще ${fakeSites.length - 10} дайвсайтов`);
    }
    console.log('');
    
    // Delete fake sites
    console.log('🗑️  Удаляю несуществующие дайвсайты...');
    
    const fakeIds = fakeSites.map(s => s.id);
    const deleteResult = await pool.query(`
      DELETE FROM dive_sites
      WHERE id = ANY($1::uuid[])
    `, [fakeIds]);
    
    console.log(`✅ Удалено ${deleteResult.rowCount} несуществующих дайвсайтов\n`);
    
    // Verify deletion
    const verifyResult = await pool.query(`
      SELECT COUNT(*) as count FROM dive_sites
    `);
    console.log(`📊 Осталось дайвсайтов в базе: ${verifyResult.rows[0].count}\n`);
    
  } catch (error) {
    console.error('❌ Ошибка при удалении дайвсайтов:');
    console.error(error);
    await pool.end();
    process.exit(1);
  }
  
  await pool.end();
  console.log('✅ Готово!\n');
}

// Run the script
deleteFakeDiveSites().catch(error => {
  console.error('❌ Критическая ошибка:');
  console.error(error);
  process.exit(1);
});
