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
import { CertificationsService } from './certifications.service';
import { CreateCertificationDto } from './dto/create-certification.dto';
import { UpdateCertificationDto } from './dto/update-certification.dto';

@ApiTags('certifications')
@Controller('users')
@UseGuards(JwtAuthGuard)
@ApiBearerAuth()
export class CertificationsController {
  constructor(private readonly certificationsService: CertificationsService) {}

  @Get(':userId/certifications')
  @ApiOperation({ summary: 'List certifications (own or friend with privacy)' })
  list(
    @Request() req: { user: { sub: string } },
    @Param('userId') userId: string,
  ) {
    return this.certificationsService.list(userId, req.user.sub);
  }

  @Post(':userId/certifications')
  @ApiOperation({ summary: 'Add a certification card' })
  create(
    @Request() req: { user: { sub: string } },
    @Param('userId') userId: string,
    @Body() dto: CreateCertificationDto,
  ) {
    return this.certificationsService.create(userId, req.user.sub, dto);
  }

  @Patch('certifications/:certificationId')
  @ApiOperation({ summary: 'Update certification (e.g. card photo URL)' })
  update(
    @Request() req: { user: { sub: string } },
    @Param('certificationId') certificationId: string,
    @Body() dto: UpdateCertificationDto,
  ) {
    return this.certificationsService.update(certificationId, req.user.sub, dto);
  }

  @Delete('certifications/:certificationId')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: 'Delete a certification' })
  async remove(
    @Request() req: { user: { sub: string } },
    @Param('certificationId') certificationId: string,
  ) {
    await this.certificationsService.delete(certificationId, req.user.sub);
  }
}
