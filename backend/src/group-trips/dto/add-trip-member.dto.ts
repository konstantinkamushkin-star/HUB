import { ApiProperty } from '@nestjs/swagger';
import { IsUUID } from 'class-validator';

export class AddTripMemberDto {
  @ApiProperty()
  @IsUUID()
  userId: string;
}
