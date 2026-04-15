import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class DeleteMyAccountDto {
  @ApiProperty({
    example: 'DELETE',
    description: 'Explicit destructive action confirmation',
  })
  @IsString()
  @MinLength(6)
  @MaxLength(32)
  confirmation: string;

  @ApiPropertyOptional({
    example: 'CurrentPassword123!',
    description: 'Required for password-based accounts; optional for OAuth-only users',
  })
  @IsOptional()
  @IsString()
  @MinLength(8)
  @MaxLength(256)
  currentPassword?: string;
}
