import { IsUUID } from 'class-validator';

export class OpenContributionSupportChatDto {
  @IsUUID()
  contributionId: string;
}
