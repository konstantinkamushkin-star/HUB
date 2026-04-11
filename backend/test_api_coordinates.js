// Test API coordinates response
const http = require('http');

const testCases = [
  { name: 'Egypt - Red Sea', lat: 27.5, lng: 34.0, radius: 100000 },
  { name: 'Maldives', lat: 3.5, lng: 73.0, radius: 100000 },
  { name: 'Indonesia - Bali', lat: -8.5, lng: 115.0, radius: 100000 },
];

async function testAPI() {
  console.log('🧪 Тестирование API координат...\n');
  
  for (const test of testCases) {
    console.log(`📡 Тест: ${test.name} (lat=${test.lat}, lng=${test.lng})`);
    
    const url = `http://localhost:3000/api/v1/dive-sites/search?lat=${test.lat}&lng=${test.lng}&radius=${test.radius}&limit=5`;
    
    try {
      const response = await fetch(url);
      const data = await response.json();
      
      if (data.success && data.data && data.data.length > 0) {
        console.log(`   ✅ Получено ${data.data.length} дайвсайтов\n`);
        
        data.data.forEach((site, i) => {
          const lat = site.latitude;
          const lng = site.longitude;
          const valid = Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
          const inRange = lat >= test.lat - 5 && lat <= test.lat + 5 && 
                         lng >= test.lng - 5 && lng <= test.lng + 5;
          
          console.log(`   ${i + 1}. ${site.name} (${site.country || 'Unknown'})`);
          console.log(`      lat=${lat}, lng=${lng}`);
          console.log(`      valid=${valid}, inRange=${inRange}`);
          
          if (!valid) {
            console.log(`      ❌ НЕВАЛИДНЫЕ КООРДИНАТЫ!`);
          } else if (!inRange) {
            console.log(`      ⚠️  Координаты вне ожидаемого диапазона`);
          }
        });
      } else {
        console.log(`   ⚠️  Нет данных или ошибка API\n`);
      }
    } catch (error) {
      console.log(`   ❌ Ошибка: ${error.message}\n`);
    }
    
    console.log('');
  }
}

// Use fetch if available, otherwise use http
if (typeof fetch !== 'undefined') {
  testAPI();
} else {
  // Fallback for Node < 18
  const https = require('https');
  async function fetch(url) {
    return new Promise((resolve, reject) => {
      https.get(url, (res) => {
        let data = '';
        res.on('data', (chunk) => { data += chunk; });
        res.on('end', () => {
          try {
            resolve({ json: () => JSON.parse(data) });
          } catch (e) {
            reject(e);
          }
        });
      }).on('error', reject);
    });
  }
  testAPI();
}
