import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  Post,
  Request,
  UseGuards,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOperation,
  ApiTags,
} from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { GroupTripsService } from './group-trips.service';
import { CreateGroupTripDto } from './dto/create-group-trip.dto';
import { AddTripMemberDto } from './dto/add-trip-member.dto';

@ApiTags('group-trips')
@Controller('group-trips')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class GroupTripsController {
  constructor(private readonly groupTripsService: GroupTripsService) {}

  @Get()
  @ApiOperation({ summary: 'List my group trips' })
  list(@Request() req: { user: { sub: string } }) {
    return this.groupTripsService.listForUser(req.user.sub);
  }

  @Get(':id')
  @ApiOperation({ summary: 'Get group trip details' })
  getOne(
    @Request() req: { user: { sub: string } },
    @Param('id') id: string,
  ) {
    return this.groupTripsService.getById(id, req.user.sub);
  }

  @Post()
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: 'Create group trip and linked group chat' })
  create(
    @Request() req: { user: { sub: string } },
    @Body() dto: CreateGroupTripDto,
  ) {
    return this.groupTripsService.create(req.user.sub, dto);
  }

  @Post(':id/members')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Invite a friend to the trip and chat' })
  addMember(
    @Request() req: { user: { sub: string } },
    @Param('id') id: string,
    @Body() dto: AddTripMemberDto,
  ) {
    return this.groupTripsService.addMember(id, req.user.sub, dto.userId);
  }
}
