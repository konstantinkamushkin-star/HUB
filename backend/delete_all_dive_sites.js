// Script to delete ALL dive sites from the database
// Use with caution! This will remove all dive sites.
const { Pool } = require('pg');

const dbConfig = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_DATABASE || 'divehub',
  user: process.env.DB_USERNAME || 'admin',
  password: process.env.DB_PASSWORD || 'admin123',
};

const pool = new Pool(dbConfig);

async function deleteAllDiveSites() {
  console.log('🗑️  Удаление всех дайвсайтов из базы данных...\n');
  
  try {
    await pool.query('SELECT 1');
    console.log('✅ Подключение к базе данных успешно\n');
  } catch (error) {
    console.error('❌ Ошибка подключения:', error.message);
    process.exit(1);
  }
  
  // Check current count
  const countResult = await pool.query('SELECT COUNT(*) as count FROM dive_sites');
  const currentCount = parseInt(countResult.rows[0].count, 10);
  
  console.log(`📊 Текущее количество дайвсайтов: ${currentCount}\n`);
  
  if (currentCount === 0) {
    console.log('✅ База данных уже пуста\n');
    await pool.end();
    return;
  }
  
  // Check for foreign key constraints
  console.log('🔍 Проверка связей с другими таблицами...\n');
  
  try {
    // Try to delete with CASCADE to handle any foreign key constraints
    const deleteResult = await pool.query('DELETE FROM dive_sites');
    console.log(`✅ Удалено ${deleteResult.rowCount} дайвсайтов\n`);
  } catch (error) {
    if (error.code === '23503') { // Foreign key violation
      console.log('⚠️  Обнаружены связи с другими таблицами\n');
      console.log('Попытка удаления с CASCADE...\n');
      
      // Try to disable foreign key checks temporarily
      try {
        await pool.query('SET session_replication_role = replica');
        const deleteResult = await pool.query('DELETE FROM dive_sites');
        await pool.query('SET session_replication_role = DEFAULT');
        console.log(`✅ Удалено ${deleteResult.rowCount} дайвсайтов\n`);
      } catch (cascadeError) {
        console.error('❌ Ошибка при удалении:', cascadeError.message);
        console.error('\n💡 Возможно, нужно вручную удалить связанные записи\n');
        throw cascadeError;
      }
    } else {
      throw error;
    }
  }
  
  // Verify deletion
  const verifyResult = await pool.query('SELECT COUNT(*) as count FROM dive_sites');
  const remainingCount = parseInt(verifyResult.rows[0].count, 10);
  
  if (remainingCount === 0) {
    console.log('✅ Все дайвсайты успешно удалены!\n');
    console.log('📊 База данных готова для заполнения реальными дайвсайтами\n');
  } else {
    console.log(`⚠️  Осталось ${remainingCount} дайвсайтов\n`);
  }
  
  await pool.end();
}

deleteAllDiveSites().catch(error => {
  console.error('❌ Ошибка:', error);
  process.exit(1);
});
