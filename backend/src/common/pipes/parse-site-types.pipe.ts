import { PipeTransform, Injectable, BadRequestException } from '@nestjs/common';

@Injectable()
export class ParseSiteTypesPipe implements PipeTransform {
  transform(value: any): string[] | undefined {
    if (value === undefined || value === null) {
      return undefined;
    }
    
    // If it's already an array, return as is
    if (Array.isArray(value)) {
      return value;
    }
    
    // If it's a string, convert to array with one element
    if (typeof value === 'string') {
      return [value];
    }
    
    throw new BadRequestException('site_types must be a string or an array of strings');
  }
}
