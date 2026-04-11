import {
  Body,
  Controller,
  HttpCode,
  HttpStatus,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { AdminPortalAuthService } from './admin-portal-auth.service';
import { LoginDto } from './dto/login.dto';
import { Admin2faVerifyDto } from './dto/admin-2fa-verify.dto';
import { AdminTotpConfirmDto } from './dto/admin-totp-confirm.dto';
import { AdminTotpDisableDto } from './dto/admin-totp-disable.dto';
import { Public } from './decorators/public.decorator';
import { JwtAuthGuard } from './guards/jwt-auth.guard';
import { PermissionsGuard } from './rbac/permissions.guard';
import { RequirePermissions } from './rbac/permissions.decorator';
import { Permission } from './rbac/permissions';

@ApiTags('auth-admin')
@Controller('auth/admin')
export class AdminPortalAuthController {
  constructor(private readonly adminPortalAuth: AdminPortalAuthService) {}

  @Post('login')
  @Public()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Admin panel login (optional second TOTP step)' })
  async adminLogin(@Body() dto: LoginDto) {
    return this.adminPortalAuth.adminLogin(dto);
  }

  @Post('2fa/verify')
  @Public()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Complete admin login after TOTP' })
  async verify2fa(@Body() dto: Admin2faVerifyDto) {
    return this.adminPortalAuth.verifyAdmin2fa(dto.preAuthToken, dto.code);
  }

  @Post('totp/setup')
  @UseGuards(JwtAuthGuard, PermissionsGuard)
  @RequirePermissions(Permission.VIEW_ADMIN_DASHBOARD)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Start TOTP enrollment (returns secret + otpauth URL)' })
  async totpSetup(@Req() req: { user: { sub: string } }) {
    return this.adminPortalAuth.beginTotpSetup(req.user.sub);
  }

  @Post('totp/confirm')
  @UseGuards(JwtAuthGuard, PermissionsGuard)
  @RequirePermissions(Permission.VIEW_ADMIN_DASHBOARD)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Confirm TOTP with a valid code' })
  async totpConfirm(
    @Req() req: { user: { sub: string } },
    @Body() dto: AdminTotpConfirmDto,
  ) {
    return this.adminPortalAuth.confirmTotpSetup(req.user.sub, dto.code);
  }

  @Post('totp/disable')
  @UseGuards(JwtAuthGuard, PermissionsGuard)
  @RequirePermissions(Permission.VIEW_ADMIN_DASHBOARD)
  @ApiBearerAuth()
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Disable admin TOTP (requires password)' })
  async totpDisable(
    @Req() req: { user: { sub: string } },
    @Body() dto: AdminTotpDisableDto,
  ) {
    return this.adminPortalAuth.disableTotp(req.user.sub, dto.password);
  }
}
