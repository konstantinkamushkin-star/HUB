import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { PermissionsGuard } from '../auth/rbac/permissions.guard';
import { RequirePermissions } from '../auth/rbac/permissions.decorator';
import { Permission } from '../auth/rbac/permissions';
import { AdminRegistryService } from './admin-registry.service';

@ApiTags('admin-registry')
@Controller('admin/registry')
@UseGuards(JwtAuthGuard, PermissionsGuard)
@ApiBearerAuth()
export class AdminRegistryController {
  constructor(private readonly registry: AdminRegistryService) {}

  @Get('dive-logs')
  @ApiOperation({ summary: 'Paginated dive logs for moderation' })
  @RequirePermissions(Permission.MODERATE_CONTENT)
  listDiveLogs(
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
    @Query('moderationStatus') moderationStatus?: string,
    @Query('userId') userId?: string,
  ) {
    return this.registry.listDiveLogs({
      limit: limit ? Number(limit) : undefined,
      offset: offset ? Number(offset) : undefined,
      moderationStatus,
      userId,
    });
  }

  @Get('feed-posts')
  @ApiOperation({ summary: 'Paginated feed posts' })
  @RequirePermissions(Permission.MODERATE_CONTENT)
  listFeedPosts(
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
    @Query('moderationStatus') moderationStatus?: string,
    @Query('userId') userId?: string,
    @Query('includeDeleted') includeDeleted?: string,
  ) {
    return this.registry.listFeedPosts({
      limit: limit ? Number(limit) : undefined,
      offset: offset ? Number(offset) : undefined,
      moderationStatus,
      userId,
      includeDeleted: includeDeleted === 'true' || includeDeleted === '1',
    });
  }

  @Get('comments')
  @ApiOperation({ summary: 'Paginated feed comments' })
  @RequirePermissions(Permission.MODERATE_CONTENT)
  listComments(
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
    @Query('moderationStatus') moderationStatus?: string,
    @Query('postId') postId?: string,
    @Query('userId') userId?: string,
    @Query('includeDeleted') includeDeleted?: string,
  ) {
    return this.registry.listComments({
      limit: limit ? Number(limit) : undefined,
      offset: offset ? Number(offset) : undefined,
      moderationStatus,
      postId,
      userId,
      includeDeleted: includeDeleted === 'true' || includeDeleted === '1',
    });
  }

  @Get('shops')
  @ApiOperation({ summary: 'Paginated shops' })
  @RequirePermissions(Permission.VERIFY_ENTITIES)
  listShops(
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
    @Query('verificationStatus') verificationStatus?: string,
    @Query('query') query?: string,
  ) {
    return this.registry.listShops({
      limit: limit ? Number(limit) : undefined,
      offset: offset ? Number(offset) : undefined,
      verificationStatus,
      query,
    });
  }

  @Get('dive-centers')
  @ApiOperation({ summary: 'Paginated dive centers' })
  @RequirePermissions(Permission.VERIFY_ENTITIES)
  listCenters(
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
    @Query('status') status?: string,
    @Query('verificationStatus') verificationStatus?: string,
    @Query('query') query?: string,
  ) {
    return this.registry.listDiveCenters({
      limit: limit ? Number(limit) : undefined,
      offset: offset ? Number(offset) : undefined,
      status,
      verificationStatus,
      query,
    });
  }

  @Get('dive-sites')
  @ApiOperation({ summary: 'Paginated dive sites' })
  @RequirePermissions(Permission.VERIFY_ENTITIES)
  listSites(
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
    @Query('status') status?: string,
    @Query('query') query?: string,
  ) {
    return this.registry.listDiveSites({
      limit: limit ? Number(limit) : undefined,
      offset: offset ? Number(offset) : undefined,
      status,
      query,
    });
  }
}
