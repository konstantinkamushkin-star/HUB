// Script to verify and fix dive site coordinates
// Checks for swapped coordinates, invalid ranges, and water location validation
const fs = require('fs');
const path = require('path');

// Country bounding boxes for validation
const COUNTRY_BBOXES = {
  'Egypt': { minLat: 22, maxLat: 32, minLng: 25, maxLng: 37 },
  'Red Sea': { minLat: 12, maxLat: 30, minLng: 32, maxLng: 45 },
  'Maldives': { minLat: -1, maxLat: 8, minLng: 72, maxLng: 75 },
  'Indonesia': { minLat: -11, maxLat: 6, minLng: 95, maxLng: 141 },
  'Philippines': { minLat: 5, maxLat: 20, minLng: 117, maxLng: 127 },
  'Caribbean': { minLat: 10, maxLat: 28, minLng: -90, maxLng: -60 },
  'Australia': { minLat: -44, maxLat: -10, minLng: 113, maxLng: 154 },
  'Thailand': { minLat: 6, maxLat: 21, minLng: 97, maxLng: 106 },
  'Malaysia': { minLat: 1, maxLat: 8, minLng: 100, maxLng: 120 },
  'Hawaii': { minLat: 18, maxLat: 23, minLng: -161, maxLng: -154 },
  'Florida': { minLat: 24, maxLat: 31, minLng: -83, maxLng: -79 },
  'California': { minLat: 32, maxLat: 42, minLng: -125, maxLng: -117 },
  'Japan': { minLat: 24, maxLat: 46, minLng: 122, maxLng: 146 },
  'Brazil': { minLat: -35, maxLat: -3, minLng: -50, maxLng: -32 },
  'Mexico': { minLat: 14, maxLat: 32, minLng: -118, maxLng: -86 },
  'Turkey': { minLat: 35, maxLat: 42, minLng: 26, maxLng: 45 },
  'Croatia': { minLat: 42, maxLat: 47, minLng: 13, maxLng: 20 },
  'Greece': { minLat: 34, maxLat: 42, minLng: 19, maxLng: 30 },
  'Spain': { minLat: 35, maxLat: 44, minLng: -10, maxLng: 5 },
  'Italy': { minLat: 36, maxLat: 47, minLng: 6, maxLng: 19 },
};

// Validate coordinates are in valid range
function isValidCoordinate(lat, lng) {
  return !isNaN(lat) && !isNaN(lng) && 
         Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
}

// Check if coordinates are swapped (common error)
function areCoordinatesSwapped(lat, lng, country = null) {
  // Obviously invalid
  if (Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    return true;
  }
  
  // Check against country-specific ranges
  if (country) {
    const bbox = COUNTRY_BBOXES[country];
    if (bbox) {
      const latInRange = lat >= bbox.minLat && lat <= bbox.maxLat;
      const lngInRange = lng >= bbox.minLng && lng <= bbox.maxLng;
      
      // If swapped, the opposite would be in range
      const swappedLatInRange = lng >= bbox.minLat && lng <= bbox.maxLat;
      const swappedLngInRange = lat >= bbox.minLng && lat <= bbox.maxLng;
      
      if (!latInRange && !lngInRange && swappedLatInRange && swappedLngInRange) {
        return true;
      }
    }
  }
  
  // Special case: Red Sea / Egypt
  if (country === 'Egypt' || country === 'Red Sea') {
    // Red Sea: lat should be 25-29, lng should be 33-35
    if (lat > 30 || lng < 30) {
      // Check if swapped would be correct
      if (lng >= 25 && lng <= 29 && lat >= 33 && lat <= 35) {
        return true;
      }
    }
  }
  
  return false;
}

// Verify and fix coordinates for a single dive site
function verifySite(site) {
  let lat = site.lat || site.latitude;
  let lng = site.lng || site.longitude;
  const country = site.country;
  
  const issues = [];
  let fixed = false;
  
  // Check if coordinates exist
  if (lat === undefined || lng === undefined || lat === null || lng === null) {
    issues.push('missing_coordinates');
    return { ...site, lat, lng, issues, valid: false };
  }
  
  // Convert to numbers
  lat = parseFloat(lat);
  lng = parseFloat(lng);
  
  // Check if valid numbers
  if (isNaN(lat) || isNaN(lng)) {
    issues.push('invalid_numbers');
    return { ...site, lat, lng, issues, valid: false };
  }
  
  // Check if in valid range
  if (!isValidCoordinate(lat, lng)) {
    issues.push('out_of_range');
    // Try swapping
    if (isValidCoordinate(lng, lat)) {
      [lat, lng] = [lng, lat];
      issues.push('swapped_fixed');
      fixed = true;
    } else {
      return { ...site, lat, lng, issues, valid: false };
    }
  }
  
  // Check if swapped based on country
  if (areCoordinatesSwapped(lat, lng, country)) {
    issues.push('swapped_detected');
    [lat, lng] = [lng, lat];
    issues.push('swapped_fixed');
    fixed = true;
  }
  
  // Round to reasonable precision (6 decimal places = ~10cm accuracy)
  lat = parseFloat(lat.toFixed(6));
  lng = parseFloat(lng.toFixed(6));
  
  return {
    ...site,
    lat,
    lng,
    latitude: lat, // Keep both for compatibility
    longitude: lng,
    issues: issues.length > 0 ? issues : null,
    fixed: fixed,
    valid: true,
  };
}

// Verify all dive sites in a file
function verifyDiveSites(inputFile, outputFile = null) {
  console.log(`🔍 Верификация координат дайвсайтов...\n`);
  console.log(`📂 Входной файл: ${inputFile}\n`);
  
  if (!fs.existsSync(inputFile)) {
    console.error(`❌ Файл не найден: ${inputFile}`);
    return null;
  }
  
  const data = JSON.parse(fs.readFileSync(inputFile, 'utf8'));
  const sites = Array.isArray(data) ? data : [];
  
  console.log(`📊 Обработка ${sites.length} дайвсайтов...\n`);
  
  const verified = [];
  const stats = {
    total: sites.length,
    valid: 0,
    invalid: 0,
    fixed: 0,
    issues: {},
  };
  
  for (const site of sites) {
    const verifiedSite = verifySite(site);
    
    if (verifiedSite.valid) {
      verified.push(verifiedSite);
      stats.valid++;
      
      if (verifiedSite.fixed) {
        stats.fixed++;
      }
      
      if (verifiedSite.issues) {
        for (const issue of verifiedSite.issues) {
          stats.issues[issue] = (stats.issues[issue] || 0) + 1;
        }
      }
    } else {
      stats.invalid++;
      console.warn(`   ⚠️  Пропущен: ${site.name || 'Unknown'} - ${verifiedSite.issues?.join(', ') || 'invalid'}`);
    }
  }
  
  console.log(`\n✅ Верификация завершена:\n`);
  console.log(`   Всего: ${stats.total}`);
  console.log(`   Валидных: ${stats.valid} (${((stats.valid / stats.total) * 100).toFixed(1)}%)`);
  console.log(`   Исправлено: ${stats.fixed}`);
  console.log(`   Невалидных: ${stats.invalid}`);
  
  if (Object.keys(stats.issues).length > 0) {
    console.log(`\n   Найденные проблемы:`);
    Object.entries(stats.issues)
      .sort((a, b) => b[1] - a[1])
      .forEach(([issue, count]) => {
        console.log(`     ${issue}: ${count}`);
      });
  }
  
  // Save verified sites
  if (outputFile) {
    fs.writeFileSync(outputFile, JSON.stringify(verified, null, 2));
    console.log(`\n💾 Сохранено ${verified.length} валидных дайвсайтов в ${outputFile}`);
  }
  
  return verified;
}

// Main function
function main() {
  const args = process.argv.slice(2);
  
  if (args.length === 0) {
    console.log('Использование: node verify_dive_site_coordinates.js <input_file> [output_file]');
    console.log('');
    console.log('Примеры:');
    console.log('  node verify_dive_site_coordinates.js dive_sites_osm_enhanced.json');
    console.log('  node verify_dive_site_coordinates.js dive_sites_osm_enhanced.json dive_sites_verified.json');
    process.exit(1);
  }
  
  const inputFile = args[0];
  const outputFile = args[1] || inputFile.replace('.json', '_verified.json');
  
  const verified = verifyDiveSites(inputFile, outputFile);
  
  if (verified && verified.length > 0) {
    console.log('\n✅ Готово!\n');
  } else {
    console.log('\n❌ Не удалось верифицировать дайвсайты\n');
    process.exit(1);
  }
}

// Run if called directly
if (require.main === module) {
  main();
}

module.exports = { verifySite, verifyDiveSites, isValidCoordinate, areCoordinatesSwapped };
