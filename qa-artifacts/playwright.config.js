export default {
  testDir: ".",
  testMatch: /beta3-ui-audit\.spec\.js/,
  timeout: 120000,
  use: {
    browserName: "chromium",
    headless: true,
    viewport: { width: 1440, height: 1000 },
  },
};
