import assert from 'node:assert/strict';

const helper = await import('../src/pages/ability/favoriteGroupState.ts').catch(() => undefined);

assert.ok(helper, 'Expected favorite group state helpers for the original default list item');

const defaultGroup = { name: '默认清单' };
const savedGroup = { id: 'favorite-1', name: '常用能力' };

assert.equal(helper.favoriteGroupKey(defaultGroup), helper.DEFAULT_FAVORITE_GROUP_KEY);
assert.equal(helper.favoriteGroupRequestId(defaultGroup), undefined);
assert.equal(helper.canMutateFavoriteGroup(defaultGroup), false);
assert.equal(helper.favoriteGroupSubtitle(defaultGroup), undefined);
assert.equal(helper.favoriteGroupKey(savedGroup), 'favorite-1');
assert.equal(helper.favoriteGroupRequestId(savedGroup), 'favorite-1');
assert.equal(helper.canMutateFavoriteGroup(savedGroup), true);
assert.equal(helper.favoriteGroupSubtitle({ ...savedGroup, abilityIds: ['ability-1'] }), undefined);
assert.equal(helper.selectFavoriteGroup([defaultGroup, savedGroup], undefined), defaultGroup);
assert.equal(helper.selectFavoriteGroup([defaultGroup, savedGroup], helper.DEFAULT_FAVORITE_GROUP_KEY), defaultGroup);
assert.equal(helper.selectFavoriteGroup([defaultGroup, savedGroup], 'favorite-1'), savedGroup);
