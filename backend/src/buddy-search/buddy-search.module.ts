import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { User } from '../users/entities/user.entity';
import { AuthModule } from '../auth/auth.module';
import { BuddySearch } from './entities/buddy-search.entity';
import { BuddySearchService } from './buddy-search.service';
import { BuddySearchController } from './buddy-search.controller';

@Module({
  imports: [TypeOrmModule.forFeature([BuddySearch, User]), AuthModule],
  controllers: [BuddySearchController],
  providers: [BuddySearchService],
  exports: [BuddySearchService],
})
export class BuddySearchModule {}
