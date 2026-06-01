const EARTH_RADIUS_KM = 6371;

export function haversineKm(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) *
      Math.cos(toRad(lat2)) *
      Math.sin(dLon / 2) ** 2;
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return EARTH_RADIUS_KM * c;
}

/** Blur coordinates ~1 km for discover list (privacy). */
export function blurCoordinates(
  lat: number,
  lon: number,
): { latitude: number; longitude: number } {
  const offsetLat = (Math.random() - 0.5) * 0.018;
  const offsetLon = (Math.random() - 0.5) * 0.018;
  return {
    latitude: lat + offsetLat,
    longitude: lon + offsetLon,
  };
}
