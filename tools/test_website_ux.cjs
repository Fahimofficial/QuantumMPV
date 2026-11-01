const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { test } = require('node:test');
const { runInNewContext } = require('node:vm');

const html = readFileSync(join(__dirname, '..', 'index.html'), 'utf8');
const scripts = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)].map(match => match[1]);

function loadPage({ storedTheme = null, blockReads = false, blockWrites = false } = {}) {
  const listeners = {};
  const elements = {};
  const root = { dataset: {} };
  for (const id of ['themeToggle', 'themeColor', 'infoFloat', 'developerSection', 'releaseVersion', 'releaseMeta']) {
    const children = {};
    elements[id] = {
      classList: { add() {}, remove() {}, contains: () => false },
      setAttribute(name, value) { this[name] = value; },
      querySelector(selector) { return children[selector] ??= {}; },
      addEventListener(name, callback) { listeners[name] = callback; },
    };
  }
  const context = {
    document: { documentElement: root, getElementById: id => elements[id] },
    window: { scrollY: 0, addEventListener() {} },
    matchMedia: () => ({ matches: false }),
    localStorage: {
      getItem() {
        if (blockReads) throw new Error('Storage blocked');
        return storedTheme;
      },
      setItem(key, value) {
        if (blockWrites) throw new Error('Storage blocked');
        storedTheme = value;
      },
    },
    setTimeout() {}, clearTimeout() {},
    fetch: async () => ({ ok: true, json: async () => ({ name: 'Test release' }) }),
  };
  for (const script of scripts) runInNewContext(script, context);
  return { root, elements, toggle: () => listeners.click(), savedTheme: () => storedTheme };
}

test('restores the saved theme and persists theme changes', () => {
  const page = loadPage({ storedTheme: 'dark' });
  assert.equal(page.root.dataset.theme, 'dark');
  page.toggle();
  assert.equal(page.root.dataset.theme, 'light');
  assert.equal(page.savedTheme(), 'light');
  assert.equal(page.elements.themeToggle['aria-label'], 'Switch to dark theme');
});

test('blocked storage reads do not stop theme switching or release information', async () => {
  const page = loadPage({ blockReads: true, blockWrites: true });
  page.toggle();
  assert.equal(page.root.dataset.theme, 'light');
  assert.equal(page.elements.themeToggle['aria-label'], 'Switch to dark theme');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(page.elements.releaseVersion.textContent, 'Test release');
});

test('blocked storage writes do not prevent a visible theme change', () => {
  const page = loadPage({ storedTheme: 'light', blockWrites: true });
  page.toggle();
  assert.equal(page.root.dataset.theme, 'dark');
  assert.equal(page.elements.themeToggle['aria-label'], 'Switch to light theme');
});
