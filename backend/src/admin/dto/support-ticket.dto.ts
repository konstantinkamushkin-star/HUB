import { IsEmail, IsIn, IsOptional, IsString, IsUUID, MaxLength, MinLength } from 'class-validator';

export class CreateSupportTicketDto {
  @IsOptional()
  @IsUUID()
  reporterUserId?: string;

  @IsOptional()
  @IsEmail()
  @MaxLength(255)
  reporterEmail?: string;

  @IsString()
  @MinLength(3)
  @MaxLength(512)
  subject: string;

  @IsString()
  @MinLength(1)
  body: string;

  @IsOptional()
  @IsString()
  @IsIn(['low', 'normal', 'high', 'urgent'])
  priority?: string;
}

export class UpdateSupportTicketDto {
  @IsOptional()
  @IsString()
  @IsIn(['open', 'pending', 'in_progress', 'resolved', 'closed'])
  status?: string;

  @IsOptional()
  @IsString()
  @IsIn(['low', 'normal', 'high', 'urgent'])
  priority?: string;

  @IsOptional()
  @IsUUID()
  assignedAdminId?: string | null;

  @IsOptional()
  @IsString()
  @MaxLength(4000)
  resolutionNote?: string | null;
}
