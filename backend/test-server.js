// Quick test to see if server can start
const { NestFactory } = require('@nestjs/core');
const { AppModule } = require('./dist/app.module');

async function test() {
  try {
    console.log('Creating NestJS app...');
    const app = await NestFactory.create(AppModule);
    console.log('App created successfully');
    
    const port = 3000;
    await app.listen(port);
    console.log(`Server listening on port ${port}`);
  } catch (error) {
    console.error('Error:', error);
    process.exit(1);
  }
}

test();
