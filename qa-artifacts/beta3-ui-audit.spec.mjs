import { test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

const baseUrl = "http://localhost:5173";
const outDir = path.resolve("qa-artifacts", "beta3-ui");

test("beta tester 3 UI audit", async ({ page, context }) => {
  await fs.mkdir(outDir, { recursive: true });
  await context.setViewportSize?.({ width: 1440, height: 1000 }).catch?.(() => {});

  const events = [];
  page.on("console", (msg) => {
    if (["error", "warning"].includes(msg.type())) {
      events.push({ type: `console:${msg.type()}`, text: msg.text(), url: page.url() });
    }
  });
  page.on("pageerror", (err) => events.push({ type: "pageerror", text: err.message, url: page.url() }));
  page.on("response", (response) => {
    const url = response.url();
    if (response.status() >= 400 && (url.startsWith("http://localhost:5173") || url.startsWith("http://localhost:8080"))) {
      events.push({ type: "http", status: response.status(), text: url, url: page.url() });
    }
  });

  async function settle() {
    await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(800);
  }

  async function analyze(route, label) {
    await page.goto(`${baseUrl}${route}`);
    await settle();
    const safe = label.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
    const screenshot = path.join(outDir, `${safe}.png`);
    await page.screenshot({ path: screenshot, fullPage: true });
    const data = await page.evaluate(() => {
      const text = document.body.innerText;
      const visible = (el) => {
        const style = getComputedStyle(el);
        const rect = el.getBoundingClientRect();
        return style.visibility !== "hidden" && style.display !== "none" && rect.width > 0 && rect.height > 0;
      };
      const labelFor = (input) => {
        if (input.id) {
          const label = document.querySelector(`label[for="${CSS.escape(input.id)}"]`);
          if (label?.innerText.trim()) return label.innerText.trim();
        }
        const parent = input.closest("label");
        return parent?.innerText.trim() || "";
      };
      const nameOf = (el) =>
        (el.getAttribute("aria-label") ||
          el.getAttribute("title") ||
          el.getAttribute("placeholder") ||
          el.innerText ||
          el.textContent ||
          labelFor(el) ||
          "").trim();
      const unnamedButtons = [...document.querySelectorAll("button, a[href]")]
        .filter(visible)
        .filter((el) => !nameOf(el))
        .map((el) => el.outerHTML.slice(0, 180));
      const unnamedFields = [...document.querySelectorAll("input:not([type=hidden]), textarea, select")]
        .filter(visible)
        .filter((el) => !nameOf(el))
        .map((el) => el.outerHTML.slice(0, 180));
      const headings = [...document.querySelectorAll("h1,h2,h3")]
        .filter(visible)
        .slice(0, 15)
        .map((el) => `${el.tagName}:${el.innerText.trim()}`);
      const alerts = [...document.querySelectorAll('[role="alert"], .text-rose-100, .text-rose-200, .text-red-500')]
        .filter(visible)
        .map((el) => el.innerText.trim())
        .filter(Boolean);
      const svgs = [...document.querySelectorAll("svg")]
        .filter(visible)
        .map((svg) => {
          const rect = svg.getBoundingClientRect();
          return { width: Math.round(rect.width), height: Math.round(rect.height), text: svg.innerText?.trim().slice(0, 120) || "" };
        });
      return {
        url: location.href,
        title: document.title,
        bodyStart: text.slice(0, 900),
        bodyLength: text.length,
        h1: [...document.querySelectorAll("h1")].map((el) => el.innerText.trim()),
        headings,
        mainCount: document.querySelectorAll("main, [role=main]").length,
        navCount: document.querySelectorAll("nav, [role=navigation]").length,
        duplicateIds: Object.entries([...document.querySelectorAll("[id]")].reduce((acc, el) => {
          acc[el.id] = (acc[el.id] || 0) + 1;
          return acc;
        }, {})).filter(([, count]) => count > 1),
        unnamedButtons,
        unnamedFields,
        alerts,
        hasLoadingText: /\bloading\b|caricamento/i.test(text),
        hasEmptyText: /\bempty\b|no .*yet|nessun|nessuna|no preview/i.test(text),
        horizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth + 2,
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
        svgs,
      };
    });
    return { label, route, screenshot, ...data };
  }

  const results = [];
  results.push(await analyze("/login", "login-initial"));

  await page.locator('input[type="email"], input[name="email"]').fill("investor@realdemo.local");
  await page.locator('input[type="password"], input[name="password"]').fill("admin");
  await Promise.all([
    page.waitForURL((url) => !url.pathname.endsWith("/login"), { timeout: 15000 }).catch(() => {}),
    page.locator('button[type="submit"], button:has-text("Sign in"), button:has-text("Login")').click(),
  ]);
  await settle();
  results.push(await analyze("/", "dashboard"));

  for (const [route, label] of [
    ["/screener", "screener"],
    ["/securities/KO", "security-detail-ko"],
    ["/securities/KO/review", "security-review-ko"],
    ["/portfolio", "portfolio"],
    ["/watchlist", "watchlist"],
    ["/seed", "seed-universe"],
    ["/universe-curation", "universe-curation"],
  ]) {
    results.push(await analyze(route, label));
  }

  await page.setViewportSize({ width: 390, height: 844 });
  for (const [route, label] of [["/screener", "mobile-screener"], ["/portfolio", "mobile-portfolio"], ["/universe-curation", "mobile-universe-curation"]]) {
    results.push(await analyze(route, label));
  }

  await fs.writeFile(path.join(outDir, "report.json"), JSON.stringify({ generatedAt: new Date().toISOString(), events, results }, null, 2));
});
