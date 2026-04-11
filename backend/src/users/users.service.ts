import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from './entities/user.entity';

function toPublicUser(user: User): Omit<User, 'password'> {
  const { password: _, ...rest } = user;
  return rest;
}

@Injectable()
export class UsersService {
  constructor(
    @InjectRepository(User)
    private readonly userRepository: Repository<User>,
  ) {}

  async search(currentUserId: string, query: string) {
    const q = query?.trim() ?? '';
    if (q.length < 2) {
      return [];
    }

    const pattern = `%${q}%`;

    const rows = await this.userRepository
      .createQueryBuilder('u')
      .where('u.id != :me', { me: currentUserId })
      .andWhere(
        '(LOWER(u.email) LIKE LOWER(:p) OR LOWER(u."firstName") LIKE LOWER(:p) OR LOWER(u."lastName") LIKE LOWER(:p) OR LOWER(CONCAT(u."firstName", \' \', u."lastName")) LIKE LOWER(:p))',
        { p: pattern },
      )
      .take(50)
      .getMany();

    return rows.map(toPublicUser);
  }

  async findById(id: string) {
    const user = await this.userRepository.findOne({ where: { id } });
    if (!user) {
      throw new NotFoundException('User not found');
    }
    return toPublicUser(user);
  }
}
