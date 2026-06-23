import type { FavoriteGroup } from '../../types/domain';

// Original default favorite list has id:null, so the UI needs a stable local key.
export const DEFAULT_FAVORITE_GROUP_KEY = '__default_favorite_group__';

export function favoriteGroupKey(group?: Pick<FavoriteGroup, 'id'>): string | undefined {
  if (!group) return undefined;
  return group.id ?? DEFAULT_FAVORITE_GROUP_KEY;
}

export function favoriteGroupRequestId(group?: Pick<FavoriteGroup, 'id'>): string | undefined {
  return group?.id;
}

export function canMutateFavoriteGroup(group?: Pick<FavoriteGroup, 'id'>): boolean {
  return Boolean(group?.id);
}

export function favoriteGroupSubtitle(_group?: FavoriteGroup): string | undefined {
  return undefined;
}

export function selectFavoriteGroup<T extends Pick<FavoriteGroup, 'id'>>(
  groups: T[],
  activeKey?: string,
): T | undefined {
  return groups.find((group) => favoriteGroupKey(group) === activeKey) ?? groups[0];
}
