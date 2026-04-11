import { SetMetadata } from '@nestjs/common';

export const DANGEROUS_ACTION_KEY = 'dangerous_action';
export const DangerousAction = (actionName: string) =>
  SetMetadata(DANGEROUS_ACTION_KEY, actionName);
