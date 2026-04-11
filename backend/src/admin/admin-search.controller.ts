import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { AdminSearchService } from './admin-search.service';

@ApiTags('admin-search')
@Controller('admin/search')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminSearchController {
  constructor(private readonly searchService: AdminSearchService) {}

  @Get()
  @ApiOperation({ summary: 'Global admin search across major entities' })
  @RequirePermissions(Permission.VIEW_ADMIN_DASHBOARD)
  search(@Query('query') query?: string, @Query('limit') limit?: string) {
    return this.searchService.globalSearch(query ?? '', limit ? Number(limit) : undefined);
  }
}
