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
  phone?: string;
  email?: string;
  website?: string;
  address?: string;
  /** View-only catalog entry from open sources — booking disabled */
  listing_only?: boolean;
  data_source?: string | null;
  /** Inverse of listing_only — partner center that can take bookings */
  is_partner?: boolean;
  locations?: unknown[];
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
