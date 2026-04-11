import { IsEnum, IsUUID } from 'class-validator';

export enum ChatPeerTypeDto {
  user = 'user',
  dive_center = 'dive_center',
  shop = 'shop',
}

export class OpenChatDto {
  @IsEnum(ChatPeerTypeDto)
  peerType: ChatPeerTypeDto;

  @IsUUID()
  peerId: string;
}
