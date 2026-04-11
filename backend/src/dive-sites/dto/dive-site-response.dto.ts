export class DiveSiteListItemDto {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  distance_meters?: number;
  site_types: string[];
  difficulty_level: number;
  depth_min?: number;
  depth_max?: number;
  average_rating: number;
  review_count: number;
  country?: string;
  region?: string;
  thumbnail_url?: string;
}

export class PaginationInfoDto {
  has_more: boolean;
  next_cursor?: string;
  limit: number;
}

export class SearchMetaDto {
  total_in_radius?: number;
  query_time_ms?: number;
}

export class DiveSiteSearchResultDto {
  success: boolean;
  data: DiveSiteListItemDto[];
  pagination?: PaginationInfoDto;
  meta?: SearchMetaDto;
}
