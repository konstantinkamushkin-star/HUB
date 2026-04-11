// Create CSV from existing JSON file
const fs = require('fs');

const jsonFile = 'dive_sites_parsed.json';
const csvFile = 'dive_sites_10000.csv';

console.log('📖 Чтение JSON файла...');
const data = JSON.parse(fs.readFileSync(jsonFile, 'utf8'));
console.log(`✅ Прочитано ${data.length} дайвсайтов\n`);

// Helper functions
function classifyType(tags) {
  if (!tags) return 'reef';
  if (tags.historic && tags.historic.includes('wreck')) return 'wreck';
  if (tags.natural === 'reef') return 'reef';
  if (tags.name && tags.name.toLowerCase().includes('cave')) return 'cave';
  return 'reef';
}

function classifyDifficulty(depth) {
  if (!depth || depth < 0) return 'Новичок';
  if (depth < 12) return 'Новичок';
  if (depth < 25) return 'Средний';
  if (depth < 40) return 'Продвинутый';
  return 'Эксперт';
}

function getEnvironment(tags) {
  if (!tags) return 'Морская';
  if (tags.natural === 'lake' || (tags.name && tags.name.toLowerCase().includes('lake'))) {
    return 'Пресная';
  }
  return 'Морская';
}

// Create CSV
const csvHeader = 'Название,Широта,Долгота,МинГлубина(m),МаксГлубина(m),СрГлубина(m),Тип,Сложность,Среда\n';

const csvRows = data.slice(0, 10000).map(site => {
  const name = (site.name || 'Unknown').replace(/,/g, ';').replace(/"/g, '""');
  const lat = site.latitude || site.lat;
  const lon = site.longitude || site.lon;
  
  // Get depth from site data or use default
  let depth = site.depth || site.min_depth || site.avg_depth || 10;
  if (depth < 0) depth = Math.abs(depth);
  
  const minDepth = depth;
  const maxDepth = depth;
  const avgDepth = depth;
  
  // Classify type
  const typeMap = {
    'wreck': 'Обломок',
    'reef': 'Риф',
    'cave': 'Пещера',
    'wall': 'Риф',
    'other': 'Другое'
  };
  const siteType = classifyType(site.tags || {});
  const type = typeMap[siteType] || 'Риф';
  
  // Classify difficulty
  const difficulty = classifyDifficulty(depth);
  
  // Get environment
  const environment = getEnvironment(site.tags || {});
  
  return `"${name}",${lat},${lon},${minDepth.toFixed(1)},${maxDepth.toFixed(1)},${avgDepth.toFixed(1)},"${type}","${difficulty}","${environment}"`;
});

const csvContent = csvHeader + csvRows.join('\n');

// Write with UTF-8 BOM for Excel
fs.writeFileSync(csvFile, '\ufeff' + csvContent, 'utf8');

console.log(`✅ CSV файл создан: ${csvFile}`);
console.log(`   Строк: ${csvRows.length + 1} (включая заголовок)\n`);

// Show sample
console.log('Пример первых 3 строк:');
console.log(csvHeader.trim());
csvRows.slice(0, 2).forEach(row => console.log(row));
