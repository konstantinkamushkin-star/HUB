import { User } from '../users/entities/user.entity';

export type DiverPrivacy = {
  showProfilePhoto?: boolean;
  showCertificationLevel?: boolean;
  showNumberOfDives?: boolean;
  showLocation?: boolean;
  showLastDive?: boolean;
  showEquipment?: boolean;
  showBuddySearchStatus?: boolean;
  showLogbook?: boolean;
  showContactOptions?: boolean;
};

export function diverProfileRecord(
  user: User,
): Record<string, unknown> | null {
  const dp = user.diverProfile;
  if (!dp || typeof dp !== 'object' || Array.isArray(dp)) {
    return null;
  }
  return dp as Record<string, unknown>;
}

export function diverPrivacy(user: User): DiverPrivacy {
  const dp = diverProfileRecord(user);
  const raw = dp?.privacy;
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return {};
  }
  return raw as DiverPrivacy;
}

export function isLookingForBuddy(user: User): boolean {
  const dp = diverProfileRecord(user);
  return dp?.lookingForBuddy === true;
}

export function isBuddySearchVisible(user: User): boolean {
  return diverPrivacy(user).showBuddySearchStatus !== false;
}

export function isDiscoverableUser(user: User): boolean {
  return (
    user.shareLocation === true &&
    user.showInFriendSearch !== false &&
    isLookingForBuddy(user) &&
    isBuddySearchVisible(user)
  );
}

export function privacyAllows(
  user: User,
  key: keyof DiverPrivacy,
  defaultAllow = true,
): boolean {
  const v = diverPrivacy(user)[key];
  if (v === undefined) {
    return defaultAllow;
  }
  return v !== false;
}
