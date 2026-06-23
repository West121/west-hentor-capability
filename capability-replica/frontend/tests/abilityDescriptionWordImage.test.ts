import assert from 'node:assert/strict';
import { pendingWordImagesFromHtml, replacePendingWordImages } from '../src/pages/ability/abilityDescriptionWordImage.ts';

const htmlWithPendingWordImages =
  '<p><img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==" width="96" height="42" alt="clip" title="本地 Word 图片待上传" word_img="file:///C:/Temp/clip_image001.png" class="word-image-pending"></p>'
  + '<p><img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==" width="120" height="80" word_img="file:///C:/Temp/clip_image002.png" class="word-image-pending"></p>';

assert.deepEqual(pendingWordImagesFromHtml(htmlWithPendingWordImages), [
  {
    key: 'file:///C:/Temp/clip_image001.png',
    wordImage: 'file:///C:/Temp/clip_image001.png',
    label: 'clip_image001.png',
    alt: 'clip',
    title: '本地 Word 图片待上传',
    width: '96',
    height: '42',
  },
  {
    key: 'file:///C:/Temp/clip_image002.png',
    wordImage: 'file:///C:/Temp/clip_image002.png',
    label: 'clip_image002.png',
    alt: '',
    title: '本地 Word 图片待上传',
    width: '120',
    height: '80',
  },
]);

assert.equal(
  replacePendingWordImages(
    htmlWithPendingWordImages,
    [
      {
        wordImage: 'file:///C:/Temp/clip_image001.png',
        uploaded: {
          state: 'SUCCESS',
          url: '/UEditor/GetImage?id=word-1',
          title: 'word-1.png',
          original: 'word-1.png',
          type: '.png',
          size: 1024,
        },
      },
    ],
    'http://localhost:9901',
  ),
  '<p><img src="http://localhost:9901/UEditor/GetImage?id=word-1" width="96" height="42" alt="word-1.png" title="word-1.png"></p><p><img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==" width="120" height="80" title="本地 Word 图片待上传" word_img="file:///C:/Temp/clip_image002.png" class="word-image-pending"></p>',
);
