import { chromium } from '../node_modules/playwright/index.mjs';
import fs from 'node:fs/promises';
const out = new URL('.', import.meta.url).pathname;
const browser = await chromium.launch();
const results = [];
for (const width of [320, 390, 768, 1440]) {
  for (const route of ['/login', '/screener', '/checklists', '/portfolio', '/watchlist']) {
    const page = await browser.newPage({viewport: {width, height:844}});
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.route('**/*', async r => {
      const p = new URL(r.request().url());
      if (p.port === '5173') return r.continue();
      let status = 200, body = {};
      if (p.pathname === '/auth/refresh') {
        status = route === '/login' ? 401 : 200;
        body = {accessToken: 'mock.'+Buffer.from(JSON.stringify({sub:'audit@example.invalid',role:'INVESTOR'})).toString('base64url')+'.mock'};
      } else if (p.pathname === '/auth/oauth2/providers') body = {google:true};
      else if (p.pathname === '/api/v1/screener') body = {results:[{symbol:'TEST',companyName:'Example Company',sector:'Technology',exchange:'NASDAQ'}],totalElements:1,totalPages:1,page:0,size:20};
      else if (/sectors|exchanges|checklists$|portfolios$|watchlist$|agent-one-comparison/.test(p.pathname)) body=[];
      else {status=503; body={message:'Simulated unavailable API for layout audit'};}
      return r.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
    });
    await page.goto('http://127.0.0.1:5173'+route);
    await page.waitForTimeout(800);
    const metrics = await page.evaluate(() => {
      const visible = e => e.getBoundingClientRect().width > 0 && e.getBoundingClientRect().height > 0;
      const rect = e => { const r=e.getBoundingClientRect(); return {x:r.x,y:r.y,width:r.width,height:r.height}; };
      return {
        pageWidth:document.documentElement.scrollWidth,
        headings:[...document.querySelectorAll('h1,h2')].map(e=>({text:e.textContent,...rect(e)})),
        fields:[...document.querySelectorAll('input,select,textarea')].filter(visible).map(e=>({tag:e.tagName,type:e.type,name:e.getAttribute('aria-label')||[...e.labels||[]].map(l=>l.textContent).join(' '),font:getComputedStyle(e).fontSize,...rect(e)})),
        scrollers:[...document.querySelectorAll('*')].filter(e=>visible(e)&&getComputedStyle(e).overflowX==='auto'&&e.scrollWidth>e.clientWidth).map(e=>({tag:e.tagName,width:e.clientWidth,scrollWidth:e.scrollWidth,...rect(e)})),
        smallTargets:[...document.querySelectorAll('button,a,select,input')].filter(e=>visible(e)&&!e.disabled&&(e.getBoundingClientRect().height<24||e.getBoundingClientRect().width<24)).map(e=>({text:(e.textContent||e.getAttribute('aria-label')||e.type).slice(0,60),...rect(e)})),
      };
    });
    results.push({width,route,errors,...metrics});
    if (width===390 || route==='/checklists'&&width===768) await page.screenshot({path:out+route.slice(1)+'-'+width+'.png',fullPage:true});
    if (width===390 && route==='/screener') {
      await page.getByRole('button', {name:'Review', exact:true}).focus();
      await page.keyboard.press('Enter');
      await page.waitForTimeout(150);
      results[results.length-1].reviewEnterDestination = new URL(page.url()).pathname;
    }
    await page.close();
  }
}
await fs.writeFile(out+'measurements.json',JSON.stringify(results,null,2));
console.log(JSON.stringify(results.map(r=>({width:r.width,route:r.route,pageWidth:r.pageWidth,errors:r.errors,unnamed:r.fields.filter(f=>!f.name).length,firstFieldY:r.fields[0]?.y,scrollers:r.scrollers})),null,2));
await browser.close();
