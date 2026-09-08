# mpvRx website

The product site and documentation are built with [Blume](https://useblume.dev/).

## Requirements

- Node.js 22.19 or newer
- npm 10 or newer

## Local development

```bash
npm install
npm run dev
```

## Validation

```bash
npm run check
npm run validate
npm run build
```

The production site is written to `dist/`.

## Vercel

Import `Riteshp2001/mpvRx` into Vercel with the repository root as the Root
Directory. The repository-level `vercel.json` installs this workspace, builds
Blume, and publishes `website/dist` automatically.

Do not set Vercel's Root Directory to `website` while using the root
`vercel.json`; its paths are intentionally relative to the repository root.

The production build includes `/docs`, `/api/docs/pages.json`,
`/api/docs/navigation.json`, `/openapi.json`, `/llms.txt`, and
`/llms-full.txt`.

## Content map

- `pages/index.astro`: custom product landing page
- `content/docs/`: user and contributor documentation
- `public/images/`: site-owned copies of app artwork and screenshots
- `blume.config.ts`: navigation, search, theme, SEO, and AI-readable output
