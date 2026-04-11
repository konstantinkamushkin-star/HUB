import { IsNotEmpty, IsUUID } from 'class-validator';

export class AddDiveCenterInstructorDto {
  @IsUUID()
  @IsNotEmpty()
  userId: string;
}
