import assert from 'node:assert/strict';
import { abilityDescriptionEditorContent, abilityDescriptionEditorOutput } from '../src/pages/ability/abilityDescriptionEditorContent.ts';

assert.equal(abilityDescriptionEditorContent(''), '<p></p>');

assert.equal(
  abilityDescriptionEditorContent('<p onclick="alert(1)">编辑<strong>说明</strong><script>alert(2)</script></p>'),
  '<p>编辑<strong>说明</strong></p>',
);

assert.equal(abilityDescriptionEditorOutput('<p></p>'), '');
assert.equal(abilityDescriptionEditorOutput('<p><br></p>'), '');
assert.equal(abilityDescriptionEditorOutput('<p><strong>说明</strong></p>'), '<p><strong>说明</strong></p>');
assert.equal(
  abilityDescriptionEditorOutput('<p><img src="/UEditor/GetImage?id=1" alt="ability.png"></p>'),
  '<p><img src="/UEditor/GetImage?id=1" alt="ability.png"></p>',
);

assert.equal(
  abilityDescriptionEditorOutput(
    '<!--[if gte mso 9]><xml><w:WordDocument></w:WordDocument></xml><![endif]--><p class="MsoNormal" style="mso-margin-top-alt:auto;mso-line-height-alt:12pt">样品<strong>要求</strong><o:p>&nbsp;</o:p></p><table class="MsoTableGrid" style="mso-padding-alt:0cm"><tbody><tr><td style="mso-padding-alt:0cm"><p class="MsoNormal">100g</p></td></tr></tbody></table>',
  ),
  '<p>样品<strong>要求</strong></p><table><tbody><tr><td><p>100g</p></td></tr></tbody></table>',
);

assert.equal(
  abilityDescriptionEditorOutput(
    '<p class="MsoNormal">流程图<v:shape style="width:120px;height:80px"><v:imagedata src="https://example.test/word-image.png" o:title="word-image"></v:imagedata></v:shape></p>',
  ),
  '<p>流程图<img src="https://example.test/word-image.png" width="120px" height="80px" title="word-image"></p>',
);

assert.equal(
  abilityDescriptionEditorOutput('<p class="MsoHeading">能力说明</p>'),
  '<p><strong>能力说明</strong></p>',
);

assert.equal(
  abilityDescriptionEditorOutput('<p><img src="file:///C:/Temp/clip_image001.png" width="96" height="42" alt="clip"></p>'),
  '<p><img src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==" width="96" height="42" alt="clip" title="本地 Word 图片待上传" word_img="file:///C:/Temp/clip_image001.png" class="word-image-pending"></p>',
);
