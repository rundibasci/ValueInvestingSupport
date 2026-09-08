import { chromium } from './node_modules/playwright/index.mjs';
import fs from 'node:fs/promises';
const out = new URL('.', import.meta.url).pathname;
const browser = await chromium.launch();
const results = [];
for (const width of [390, 768]) {
  const page = await browser.newPage({viewport: {width, height: 844}});
  await page.route('**/*', async r => {
    const p = new URL(r.request().url());
    if (p.port === '5174') return r.continue();
    let status = 200, body = {};
    if (p.pathname === '/auth/refresh') body = {accessToken: 'mock.'+Buffer.from(JSON.stringify({sub:'audit@example.invalid',role:'INVESTOR'})).toString('base64url')+'.mock'};
    else if (p.pathname === '/auth/oauth2/providers') body = {google:true};
    else if (/checklists$/.test(p.pathname)) body = [];
    else { status=503; body={message:'unavailable'}; }
    return r.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
  });
  await page.goto('http://localhost:5174/checklists');
  await page.waitForTimeout(600);
  const metrics = await page.evaluate(() => {
    const visible = e => e.getBoundingClientRect().width > 0 && e.getBoundingClientRect().height > 0;
    const rect = e => { const r=e.getBoundingClientRect(); return {x:r.x,y:r.y,width:r.width,height:r.height}; };
    return {
      pageWidth: document.documentElement.scrollWidth,
      fields: [...document.querySelectorAll('input,select,textarea')].filter(visible).map(e=>({tag:e.tagName,type:e.type,name:e.getAttribute('aria-label')||[...e.labels||[]].map(l=>l.textContent).join(' '),...rect(e)})),
    };
  });
  results.push({width, unnamed: metrics.fields.filter(f=>!f.name).length, fields: metrics.fields});
  await page.screenshot({path: out+'checklist-verify-'+width+'.png', fullPage: true});
  await page.close();
}
await fs.writeFile(out+'checklist-verify.json', JSON.stringify(results, null, 2));
console.log(JSON.stringify(results.map(r=>({width:r.width, unnamed:r.unnamed, firstFieldWidth:r.fields[2]?.width})), null, 2));
await browser.close();
