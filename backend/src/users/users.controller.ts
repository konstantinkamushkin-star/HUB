import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Post,
  Query,
  Request,
  UseGuards,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOperation,
  ApiResponse,
  ApiTags,
} from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { UsersService } from './users.service';
import { PushService } from '../push/push.service';
import { RegisterPushTokenDto } from './dto/register-push-token.dto';

@ApiTags('users')
@Controller('users')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class UsersController {
  constructor(
    private readonly usersService: UsersService,
    private readonly pushService: PushService,
  ) {}

  @Post('me/push-token')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Register device token for push (APNs/FCM wiring comes later)',
  })
  async registerPushToken(
    @Request() req: { user: { sub: string } },
    @Body() dto: RegisterPushTokenDto,
  ) {
    await this.pushService.registerToken(
      req.user.sub,
      dto.token,
      dto.platform ?? 'ios',
    );
    return { ok: true };
  }

  @Get('search')
  @ApiOperation({ summary: 'Search users by email or name (min 2 chars)' })
  @ApiResponse({ status: 200, description: 'Matching users (max 50)' })
  async search(
    @Request() req: { user: { sub: string } },
    @Query('query') query?: string,
  ) {
    return this.usersService.search(req.user.sub, query ?? '');
  }

  @Get(':id')
  @ApiOperation({ summary: 'Get public user profile by id' })
  @ApiResponse({ status: 404, description: 'User not found' })
  async getById(@Param('id') id: string) {
    return this.usersService.findById(id);
  }
}
