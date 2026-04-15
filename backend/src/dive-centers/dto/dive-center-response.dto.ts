export class DiveCenterListItemDto {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  distance_meters?: number;
  services: string[];
  average_rating: number;
  review_count: number;
  country?: string;
  city?: string;
  thumbnail_url?: string;
  photos?: string[]; // Full array of photo URLs
  certification_agency?: string;
  nitrox_available: boolean;
  price_from?: number;
  /** Public profile text when loaded by id (optional on list/search payloads). */
  description?: string;
}

export class PaginationInfoDto {
  has_more: boolean;
  next_cursor?: string;
  limit: number;
}

export class SearchMetaDto {
  query_time_ms?: number;
}

export class DiveCenterSearchResultDto {
  success: boolean;
  data: DiveCenterListItemDto[];
  pagination?: PaginationInfoDto;
  meta?: SearchMetaDto;
}
