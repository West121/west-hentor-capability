import assert from 'node:assert/strict';
import { safeAbilityDescriptionHtml } from '../src/pages/ability/abilityDescriptionHtml.ts';

assert.equal(safeAbilityDescriptionHtml(''), '暂无说明');

assert.equal(
  safeAbilityDescriptionHtml('<p><strong>注意</strong></p><ul><li>样品量充足</li></ul>'),
  '<p><strong>注意</strong></p><ul><li>样品量充足</li></ul>',
);

assert.equal(
  safeAbilityDescriptionHtml('<p onclick="alert(1)">说明<script>alert(2)</script></p><a href="javascript:alert(3)">链接</a>'),
  '<p>说明</p><a>链接</a>',
);

assert.equal(
  safeAbilityDescriptionHtml(
    '<p><img src="/UEditor/GetImage?id=1" alt="ability.png" title="ability.png" onerror="alert(1)" style="width:9999px" /></p>',
  ),
  '<p><img src="/UEditor/GetImage?id=1" alt="ability.png" title="ability.png"></p>',
);

assert.equal(
  safeAbilityDescriptionHtml('<p><img src="/UEditor/GetImage?id=1&amp;fileName=ability.png" alt="ability.png"></p>'),
  '<p><img src="/UEditor/GetImage?id=1&amp;fileName=ability.png" alt="ability.png"></p>',
);

assert.equal(
  safeAbilityDescriptionHtml('<p><img src="javascript:alert(1)" alt="bad"></p>'),
  '<p><img alt="bad"></p>',
);

assert.equal(
  safeAbilityDescriptionHtml(
    '<p align="center" style="color:#c00000;background-color:rgb(255, 242, 204);position:absolute;background-image:url(javascript:alert(1))">居中说明</p><span style="text-align:right;color:expression(alert(1))">右侧文字</span>',
  ),
  '<p style="text-align:center;color:#c00000;background-color:rgb(255, 242, 204)">居中说明</p><span style="text-align:right">右侧文字</span>',
);

assert.equal(
  safeAbilityDescriptionHtml(
    '<p style="font-size:18px;font-family:Microsoft YaHei;line-height:1.8;behavior:url(bad.htc)"><s>旧说明</s><code>CNAS</code></p>',
  ),
  '<p style="font-size:18px;font-family:Microsoft YaHei;line-height:1.8"><s>旧说明</s><code>CNAS</code></p>',
);

assert.equal(
  safeAbilityDescriptionHtml(
    '<table border="1" cellpadding="2" cellspacing="0" onclick="alert(1)"><tbody><tr><td colspan="2" rowspan="3" style="text-align:center;color:#ff0000;width:500px" onmouseover="alert(2)">合并能力</td></tr></tbody></table>',
  ),
  '<table border="1" cellpadding="2" cellspacing="0"><tbody><tr><td colspan="2" rowspan="3" style="text-align:center;color:#ff0000">合并能力</td></tr></tbody></table>',
);

assert.equal(
  safeAbilityDescriptionHtml(
    '<table width="100%" border="0" cellpadding="0" cellspacing="0" onclick="alert(1)"><tbody><tr><td width="240" height="36">规格</td><th width="25%" height="1.5cm">要求</th></tr></tbody></table>',
  ),
  '<table width="100%" border="0" cellpadding="0" cellspacing="0"><tbody><tr><td width="240" height="36">规格</td><th width="25%" height="1.5cm">要求</th></tr></tbody></table>',
);
