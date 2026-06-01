import {
  Controller,
  Get,
  Post,
  Delete,
  Body,
  Param,
  UseGuards,
  Request,
  HttpCode,
  HttpStatus,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOperation,
  ApiResponse,
  ApiTags,
} from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { FriendsService } from './friends.service';
import { SendFriendRequestDto } from './dto/send-friend-request.dto';
import { LocationService } from '../location/location.service';
import { Query } from '@nestjs/common';

@ApiTags('friends')
@Controller('friends')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class FriendsController {
  constructor(
    private readonly friendsService: FriendsService,
    private readonly locationService: LocationService,
  ) {}

  @Get()
  @ApiOperation({ summary: 'List accepted friends' })
  @ApiResponse({ status: 200, description: 'Friends list' })
  async listFriends(@Request() req: { user: { sub: string } }) {
    return this.friendsService.listFriends(req.user.sub);
  }

  @Get('locations')
  @ApiOperation({ summary: 'Friend locations (shareLocation enabled)' })
  listFriendLocations(
    @Request() req: { user: { sub: string } },
    @Query('lat') lat?: string,
    @Query('lng') lng?: string,
  ) {
    const viewerLat = lat !== undefined ? parseFloat(lat) : undefined;
    const viewerLng = lng !== undefined ? parseFloat(lng) : undefined;
    return this.locationService.listFriendLocations(
      req.user.sub,
      viewerLat,
      viewerLng,
    );
  }

  @Get('requests/sent')
  @ApiOperation({ summary: 'Outgoing pending friend requests' })
  async listSent(@Request() req: { user: { sub: string } }) {
    return this.friendsService.listSent(req.user.sub);
  }

  @Get('requests/received')
  @ApiOperation({ summary: 'Incoming pending friend requests' })
  async listReceived(@Request() req: { user: { sub: string } }) {
    return this.friendsService.listReceived(req.user.sub);
  }

  @Post('requests')
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: 'Send a friend request' })
  @ApiResponse({ status: 400, description: 'Already friends or pending' })
  async sendRequest(
    @Request() req: { user: { sub: string } },
    @Body() dto: SendFriendRequestDto,
  ) {
    await this.friendsService.sendRequest(req.user.sub, dto);
    return {};
  }

  @Post('requests/:userId/accept')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({
    summary: 'Accept a friend request (userId = requester)',
  })
  async accept(
    @Request() req: { user: { sub: string } },
    @Param('userId') requesterId: string,
  ) {
    await this.friendsService.accept(req.user.sub, requesterId);
    return {};
  }

  @Delete('requests/:friendshipId')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Decline / cancel a pending request (by id)' })
  async decline(
    @Request() req: { user: { sub: string } },
    @Param('friendshipId') friendshipId: string,
  ) {
    await this.friendsService.decline(req.user.sub, friendshipId);
    return {};
  }
}
