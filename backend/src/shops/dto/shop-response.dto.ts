export class ShopListItemDto {
  id: string;
  name: string;
  description: string;
  localizedName?: Record<string, string>;
  localizedDescription?: Record<string, string>;
  type: string;
  brands: string[];
  serviceAvailable: boolean;
  latitude: number;
  longitude: number;
  country?: string;
  city?: string;
  address?: string;
  email?: string;
  phone?: string;
  website?: string;
  photoUrls: string[];
  averageRating: number;
  reviewCount: number;
  ownerId?: string;
  createdAt: Date;
  updatedAt: Date;
}

export class ShopSearchResultDto {
  success: boolean;
  data: ShopListItemDto[];
  cursor?: string;
  total?: number;
}
