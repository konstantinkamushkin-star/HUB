// Script to enrich dive sites with additional data
// Adds depth, difficulty, site types, and other metadata based on patterns and heuristics
const fs = require('fs');
const path = require('path');

// Site type patterns
const SITE_TYPE_PATTERNS = {
  wreck: [
    'wreck', 'shipwreck', 'ship', 'vessel', 'boat', 'ferry', 'cargo', 'tanker',
    'ss ', 'mv ', 'uss ', 'hms ', 'rms ', 'sank', 'sunk', 'sunken'
  ],
  reef: [
    'reef', 'coral', 'garden', 'wall', 'drop', 'slope', 'ledge', 'ridge',
    'shark', 'turtle', 'fish', 'marine', 'underwater'
  ],
  wall: [
    'wall', 'drop-off', 'dropoff', 'vertical', 'cliff', 'face', 'sheer'
  ],
  cave: [
    'cave', 'cavern', 'grotto', 'tunnel', 'passage', 'chamber', 'cathedral'
  ],
  drift: [
    'drift', 'current', 'flow', 'stream'
  ],
  shore: [
    'shore', 'beach', 'coast', 'landing', 'entry', 'exit'
  ],
  boat: [
    'boat', 'boat dive', 'offshore', 'open water', 'blue water'
  ],
};

// Difficulty estimation based on depth and site type
function estimateDifficulty(site) {
  // If already has difficulty, return it
  if (site.difficulty !== null && site.difficulty !== undefined) {
    return site.difficulty;
  }
  
  const depthMax = site.depthMax || site.maxDepth || 0;
  const depthMin = site.depthMin || site.minDepth || 0;
  const avgDepth = (depthMax + depthMin) / 2;
  const siteTypes = site.siteTypes || [];
  const name = (site.name || '').toLowerCase();
  const description = (site.description || '').toLowerCase();
  
  // Expert: very deep (>40m) or caves/wrecks at depth
  if (depthMax > 40 || (siteTypes.includes('cave') && depthMax > 30)) {
    return 4;
  }
  
  // Advanced: deep (30-40m) or challenging conditions
  if (depthMax > 30 || 
      (siteTypes.includes('wreck') && depthMax > 20) ||
      (siteTypes.includes('cave') && depthMax > 20) ||
      name.includes('advanced') || description.includes('advanced')) {
    return 3;
  }
  
  // Intermediate: moderate depth (15-30m) or some challenges
  if (depthMax > 15 || 
      (siteTypes.includes('wall') && depthMax > 10) ||
      (siteTypes.includes('drift') && depthMax > 10) ||
      name.includes('intermediate') || description.includes('intermediate')) {
    return 2;
  }
  
  // Beginner: shallow (<15m) and easy conditions
  return 1;
}

// Estimate depth based on site type and name
function estimateDepth(site) {
  // If already has depth, return it
  if (site.depthMax !== null && site.depthMax !== undefined) {
    return {
      min: site.depthMin || Math.max(0, site.depthMax - 10),
      max: site.depthMax,
    };
  }
  
  const siteTypes = site.siteTypes || [];
  const name = (site.name || '').toLowerCase();
  const description = (site.description || '').toLowerCase();
  
  // Extract depth from name or description
  const depthMatch = (name + ' ' + description).match(/(\d+)[-–—]?(\d+)?\s*m(?:eters?|etres?)?/i);
  if (depthMatch) {
    const min = parseInt(depthMatch[1]);
    const max = depthMatch[2] ? parseInt(depthMatch[2]) : min + 5;
    return { min: Math.max(0, min - 2), max: max + 2 };
  }
  
  // Estimate based on site type
  if (siteTypes.includes('wreck')) {
    // Wrecks: typically 15-40m, some deeper
    return { min: 10, max: 35 };
  }
  
  if (siteTypes.includes('wall')) {
    // Walls: can be shallow at top, very deep
    return { min: 5, max: 40 };
  }
  
  if (siteTypes.includes('cave')) {
    // Caves: typically 15-30m
    return { min: 10, max: 25 };
  }
  
  if (siteTypes.includes('reef')) {
    // Reefs: typically 5-25m
    return { min: 3, max: 20 };
  }
  
  if (siteTypes.includes('shore')) {
    // Shore dives: typically shallow
    return { min: 2, max: 15 };
  }
  
  // Default: moderate depth
  return { min: 5, max: 20 };
}

// Detect site types from name and description
function detectSiteTypes(site) {
  const existingTypes = site.siteTypes || [];
  if (existingTypes.length > 0) {
    return existingTypes;
  }
  
  const name = (site.name || '').toLowerCase();
  const description = (site.description || '').toLowerCase();
  const text = name + ' ' + description;
  
  const detectedTypes = [];
  
  for (const [type, patterns] of Object.entries(SITE_TYPE_PATTERNS)) {
    for (const pattern of patterns) {
      if (text.includes(pattern)) {
        detectedTypes.push(type);
        break; // Found one pattern for this type, move to next type
      }
    }
  }
  
  // Default to reef if nothing detected
  if (detectedTypes.length === 0) {
    detectedTypes.push('reef');
  }
  
  return [...new Set(detectedTypes)]; // Remove duplicates
}

// Enrich a single dive site
function enrichSite(site) {
  const enriched = { ...site };
  
  // Detect site types
  enriched.siteTypes = detectSiteTypes(enriched);
  
  // Estimate depth
  const depth = estimateDepth(enriched);
  enriched.depthMin = depth.min;
  enriched.depthMax = depth.max;
  enriched.minDepth = depth.min; // Keep both for compatibility
  enriched.maxDepth = depth.max;
  
  // Estimate difficulty
  enriched.difficulty = estimateDifficulty(enriched);
  enriched.difficultyLevel = enriched.difficulty; // Keep both for compatibility
  
  // Add metadata
  enriched.enriched = true;
  enriched.enrichedAt = new Date().toISOString();
  
  return enriched;
}

// Enrich all dive sites in a file
function enrichDiveSites(inputFile, outputFile = null) {
  console.log(`✨ Обогащение данных дайвсайтов...\n`);
  console.log(`📂 Входной файл: ${inputFile}\n`);
  
  if (!fs.existsSync(inputFile)) {
    console.error(`❌ Файл не найден: ${inputFile}`);
    return null;
  }
  
  const data = JSON.parse(fs.readFileSync(inputFile, 'utf8'));
  const sites = Array.isArray(data) ? data : [];
  
  console.log(`📊 Обработка ${sites.length} дайвсайтов...\n`);
  
  const enriched = [];
  const stats = {
    total: sites.length,
    withDepth: 0,
    withDifficulty: 0,
    withSiteTypes: 0,
    depthAdded: 0,
    difficultyAdded: 0,
    siteTypesAdded: 0,
  };
  
  for (const site of sites) {
    const beforeDepth = site.depthMax !== null && site.depthMax !== undefined;
    const beforeDifficulty = site.difficulty !== null && site.difficulty !== undefined;
    const beforeSiteTypes = site.siteTypes && site.siteTypes.length > 0;
    
    const enrichedSite = enrichSite(site);
    
    if (enrichedSite.depthMax) stats.withDepth++;
    if (enrichedSite.difficulty) stats.withDifficulty++;
    if (enrichedSite.siteTypes && enrichedSite.siteTypes.length > 0) stats.withSiteTypes++;
    
    if (!beforeDepth && enrichedSite.depthMax) stats.depthAdded++;
    if (!beforeDifficulty && enrichedSite.difficulty) stats.difficultyAdded++;
    if (!beforeSiteTypes && enrichedSite.siteTypes && enrichedSite.siteTypes.length > 0) stats.siteTypesAdded++;
    
    enriched.push(enrichedSite);
  }
  
  console.log(`\n✅ Обогащение завершено:\n`);
  console.log(`   Всего: ${stats.total}`);
  console.log(`   С глубиной: ${stats.withDepth} (${((stats.withDepth / stats.total) * 100).toFixed(1)}%)`);
  console.log(`   Со сложностью: ${stats.withDifficulty} (${((stats.withDifficulty / stats.total) * 100).toFixed(1)}%)`);
  console.log(`   С типами: ${stats.withSiteTypes} (${((stats.withSiteTypes / stats.total) * 100).toFixed(1)}%)`);
  console.log(`\n   Добавлено:`);
  console.log(`     Глубина: ${stats.depthAdded}`);
  console.log(`     Сложность: ${stats.difficultyAdded}`);
  console.log(`     Типы: ${stats.siteTypesAdded}`);
  
  // Save enriched sites
  if (outputFile) {
    fs.writeFileSync(outputFile, JSON.stringify(enriched, null, 2));
    console.log(`\n💾 Сохранено ${enriched.length} обогащенных дайвсайтов в ${outputFile}`);
  }
  
  return enriched;
}

// Main function
function main() {
  const args = process.argv.slice(2);
  
  if (args.length === 0) {
    console.log('Использование: node enrich_dive_sites.js <input_file> [output_file]');
    console.log('');
    console.log('Примеры:');
    console.log('  node enrich_dive_sites.js dive_sites_verified.json');
    console.log('  node enrich_dive_sites.js dive_sites_verified.json dive_sites_enriched.json');
    process.exit(1);
  }
  
  const inputFile = args[0];
  const outputFile = args[1] || inputFile.replace('.json', '_enriched.json');
  
  const enriched = enrichDiveSites(inputFile, outputFile);
  
  if (enriched && enriched.length > 0) {
    console.log('\n✅ Готово!\n');
  } else {
    console.log('\n❌ Не удалось обогатить дайвсайты\n');
    process.exit(1);
  }
}

// Run if called directly
if (require.main === module) {
  main();
}

module.exports = { enrichSite, enrichDiveSites, estimateDifficulty, estimateDepth, detectSiteTypes };
