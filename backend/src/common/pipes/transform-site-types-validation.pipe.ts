import { ValidationPipe, ArgumentMetadata, Injectable } from '@nestjs/common';

@Injectable()
export class TransformSiteTypesValidationPipe extends ValidationPipe {
  async transform(value: any, metadata: ArgumentMetadata) {
    // Transform site_types from string to array if needed
    if (value && typeof value === 'object' && 'site_types' in value) {
      if (value.site_types !== undefined && value.site_types !== null) {
        if (!Array.isArray(value.site_types)) {
          value.site_types = [value.site_types];
        }
      }
    }
    
    // Call parent transform which will validate
    return super.transform(value, metadata);
  }
}
