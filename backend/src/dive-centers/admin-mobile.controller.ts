import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Patch,
  Post,
  Request,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { CenterGearService } from './center-gear.service';
import { CenterInventoryService } from './center-inventory.service';

/**
 * Mobile admin routes matching iOS `NetworkService` paths under `/api/admin/...`.
 */
@ApiTags('admin-mobile')
@Controller('admin')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class AdminMobileController {
  constructor(
    private readonly gear: CenterGearService,
    private readonly inventory: CenterInventoryService,
  ) {}

  @Get('centers/:centerId/gear')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'List gear items for a dive center' })
  async listGear(
    @Param('centerId') centerId: string,
    @Request() req: { user: { sub: string; role?: string } },
  ) {
    return this.gear.list(centerId, req.user.sub, req.user.role);
  }

  @Post('centers/:centerId/gear')
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: 'Create gear item' })
  async createGear(
    @Param('centerId') centerId: string,
    @Body()
    body: {
      name: string;
      category?: string;
      manufacturer?: string | null;
      status?: string;
      condition?: string;
    },
    @Request() req: { user: { sub: string; role?: string } },
  ) {
    return this.gear.create(centerId, body, req.user.sub, req.user.role);
  }

  @Patch('gear/:gearId/status')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Update gear status' })
  async patchGearStatus(
    @Param('gearId') gearId: string,
    @Body() body: { status: string },
    @Request() req: { user: { sub: string; role?: string } },
  ) {
    return this.gear.patchStatus(
      gearId,
      body.status,
      req.user.sub,
      req.user.role,
    );
  }

  @Get('centers/:centerId/inventory/items')
  @HttpCode(HttpStatus.OK)
  async listInvItems(
    @Param('centerId') centerId: string,
    @Request() req: { user: { sub: string; role?: string } },
  ) {
    return this.inventory.listItems(centerId, req.user.sub, req.user.role);
  }

  @Post('centers/:centerId/inventory/items')
  @HttpCode(HttpStatus.OK)
  async saveInvItem(
    @Param('centerId') centerId: string,
    @Body() body: Record<string, unknown>,
    @Request() req: { user: { sub: string; role?: string } },
  ) {
    return this.inventory.upsertItem(
      centerId,
      body,
      req.user.sub,
      req.user.role,
    );
  }

  @Delete('inventory/items/:itemId')
  @HttpCode(HttpStatus.NO_CONTENT)
  async deleteInvItem(
    @Param('itemId') itemId: string,
    @Request() req: { user: { sub: string; role?: string } },
  ) {
    await this.inventory.deleteItem(itemId, req.user.sub, req.user.role);
  }

  @Get('centers/:centerId/inventory/tickets')
  @HttpCode(HttpStatus.OK)
  async listTickets(
    @Param('centerId') centerId: string,
    @Request() req: { user: { sub: string; role?: string } },
  ) {
    return this.inventory.listTickets(centerId, req.user.sub, req.user.role);
  }

  @Post('centers/:centerId/inventory/tickets')
  @HttpCode(HttpStatus.OK)
  async saveTicket(
    @Param('centerId') centerId: string,
    @Body() body: Record<string, unknown>,
    @Request() req: { user: { sub: string; role?: string } },
  ) {
    return this.inventory.upsertTicket(
      centerId,
      body,
      req.user.sub,
      req.user.role,
    );
  }
}
