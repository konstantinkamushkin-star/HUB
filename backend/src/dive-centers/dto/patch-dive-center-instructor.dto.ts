import { IsOptional, IsString, MaxLength } from 'class-validator';

export class PatchDiveCenterInstructorDto {
  @IsOptional()
  @IsString()
  @MaxLength(8000)
  bio?: string | null;
}
