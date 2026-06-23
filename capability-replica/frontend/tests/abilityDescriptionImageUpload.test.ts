import assert from 'node:assert/strict';
import { uploadedImageAttributes } from '../src/pages/ability/abilityDescriptionImageUpload.ts';

assert.deepEqual(
  uploadedImageAttributes({
    state: 'SUCCESS',
    url: '/UEditor/GetImage?id=image-1',
    title: 'ability.png',
    original: 'ability.png',
    type: '.png',
    size: 70,
  }, 'http://localhost:9901'),
  { src: 'http://localhost:9901/UEditor/GetImage?id=image-1', alt: 'ability.png', title: 'ability.png' },
);

assert.throws(
  () =>
    uploadedImageAttributes({
      state: 'IncorrectImageFormat',
      url: '',
      title: '',
      original: '',
      type: '',
      size: 0,
    }),
  /IncorrectImageFormat/,
);
