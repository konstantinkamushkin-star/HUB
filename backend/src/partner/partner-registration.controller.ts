import { Body, Controller, HttpCode, HttpStatus, Post } from '@nestjs/common';
import { ApiOperation, ApiTags } from '@nestjs/swagger';
import { PartnerRegistrationService } from './partner-registration.service';
import { SubmitPartnerRegistrationDto } from './dto/submit-partner-registration.dto';

@ApiTags('partner-registration')
@Controller('v1/partner-registrations')
export class PartnerRegistrationController {
  constructor(private readonly partner: PartnerRegistrationService) {}

  @Post()
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({
    summary: 'Публичная заявка на дайв-центр или магазин (ожидает верификации супер-админом)',
  })
  submit(@Body() dto: SubmitPartnerRegistrationDto) {
    return this.partner.submit(dto);
  }
}
