import {
  Body,
  Controller,
  Delete,
  Get,
  Headers,
  Param,
  Patch,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { DangerousAction } from './dangerous-action.decorator';
import { DangerousActionGuard } from './dangerous-action.guard';
import { AdminCmsPagesService } from './admin-cms-pages.service';
import { CreateCmsPageDto, UpdateCmsPageDto } from './dto/cms-page.dto';
import { ModerationActionDto } from './dto/moderation-action.dto';

@ApiTags('admin-cms')
@Controller('admin/cms/pages')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminCmsPagesController {
  constructor(private readonly service: AdminCmsPagesService) {}

  @Get()
  @RequirePermissions(Permission.MANAGE_CMS)
  list(
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
    @Query('status') status?: string,
    @Query('locale') locale?: string,
  ) {
    return this.service.list({
      limit: limit ? Number(limit) : undefined,
      offset: offset ? Number(offset) : undefined,
      status,
      locale,
    });
  }

  @Get('by-slug/:slug')
  @ApiOperation({ summary: 'Get CMS page by slug and locale' })
  @RequirePermissions(Permission.MANAGE_CMS)
  getBySlug(@Param('slug') slug: string, @Query('locale') locale = 'ru') {
    return this.service.getBySlug(slug, locale);
  }

  @Get(':id')
  @RequirePermissions(Permission.MANAGE_CMS)
  getOne(@Param('id') id: string) {
    return this.service.getOne(id);
  }

  @Post()
  @RequirePermissions(Permission.MANAGE_CMS)
  create(
    @Body() dto: CreateCmsPageDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('x-forwarded-for') forwardedFor?: string,
    @Headers('user-agent') userAgent?: string,
  ) {
    return this.service.create(dto, {
      adminId: req.user?.sub,
      ip: forwardedFor ?? null,
      userAgent: userAgent ?? null,
    });
  }

  @Patch(':id')
  @RequirePermissions(Permission.MANAGE_CMS)
  update(
    @Param('id') id: string,
    @Body() dto: UpdateCmsPageDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('x-forwarded-for') forwardedFor?: string,
    @Headers('user-agent') userAgent?: string,
  ) {
    return this.service.update(id, dto, {
      adminId: req.user?.sub,
      ip: forwardedFor ?? null,
      userAgent: userAgent ?? null,
    });
  }

  @Delete(':id')
  @DangerousAction('cms-page-delete')
  @UseGuards(DangerousActionGuard)
  @RequirePermissions(Permission.MANAGE_CMS)
  remove(
    @Param('id') id: string,
    @Body() dto: ModerationActionDto,
    @Req() req: { user?: { sub?: string } },
    @Headers('x-forwarded-for') forwardedFor?: string,
    @Headers('user-agent') userAgent?: string,
  ) {
    return this.service.remove(id, {
      adminId: req.user?.sub,
      ip: forwardedFor ?? null,
      userAgent: userAgent ?? null,
      reason: dto.reason,
    });
  }
}
