import {
  Controller,
  Get,
  Post,
  Body,
  Param,
  Query,
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
import { FeedService } from './feed.service';
import { CreateFeedPostDto } from './dto/create-feed-post.dto';
import { CreateFeedCommentDto } from './dto/create-feed-comment.dto';

@ApiTags('feed')
@Controller('feed')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class FeedController {
  constructor(private readonly feedService: FeedService) {}

  @Get('posts')
  @ApiOperation({ summary: 'Friends + own posts (cursor pagination)' })
  async listPosts(
    @Request() req: { user: { sub: string } },
    @Query('limit') limitStr?: string,
    @Query('cursor') cursor?: string,
  ) {
    const limit = limitStr ? parseInt(limitStr, 10) : undefined;
    return this.feedService.listPosts(
      req.user.sub,
      Number.isFinite(limit) ? limit : undefined,
      cursor || null,
    );
  }

  @Get('profile/:userId/posts')
  @ApiOperation({
    summary: 'Posts by one user (self or accepted friend)',
  })
  async listProfilePosts(
    @Request() req: { user: { sub: string } },
    @Param('userId') userId: string,
    @Query('limit') limitStr?: string,
    @Query('cursor') cursor?: string,
  ) {
    const limit = limitStr ? parseInt(limitStr, 10) : undefined;
    return this.feedService.listPostsForProfile(
      req.user.sub,
      userId,
      Number.isFinite(limit) ? limit : undefined,
      cursor || null,
    );
  }

  @Post('posts')
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: 'Create a feed post' })
  async createPost(
    @Request() req: { user: { sub: string } },
    @Body() dto: CreateFeedPostDto,
  ) {
    return this.feedService.createPost(req.user.sub, dto);
  }

  @Post('posts/:postId/like')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'Toggle like on a post' })
  async toggleLike(
    @Request() req: { user: { sub: string } },
    @Param('postId') postId: string,
  ) {
    return this.feedService.toggleLike(req.user.sub, postId);
  }

  @Get('posts/:postId/comments')
  @ApiOperation({ summary: 'List comments on a post' })
  async listComments(
    @Request() req: { user: { sub: string } },
    @Param('postId') postId: string,
  ) {
    return this.feedService.listComments(req.user.sub, postId);
  }

  @Post('posts/:postId/comments')
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: 'Add a comment' })
  async addComment(
    @Request() req: { user: { sub: string } },
    @Param('postId') postId: string,
    @Body() dto: CreateFeedCommentDto,
  ) {
    return this.feedService.addComment(req.user.sub, postId, dto.content);
  }
}
