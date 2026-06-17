# docs-viewer

Static assets for the docsify-powered documentation viewer served at `/docs`.

## Contents

| File               | Source                                    | Notes                                      |
|--------------------|-------------------------------------------|--------------------------------------------|
| `vue.css`          | `node_modules/docsify/lib/themes/vue.css` | Google Fonts `@import` removed — see below |
| `/docs/home.md`    | hand-written                              | Served as the docsify homepage             |
| `_sidebar.md`      | hand-written                              | Docsify sidebar navigation                 |

`docsify.min.js` is copied directly from `node_modules/docsify/lib/docsify.min.js` by the
Gradle `copyDocsViewerAssets` task and is not stored here.

## Upgrading docsify

1. Bump the version in `frontend/package.json` and run `npm install`.
2. Copy the new theme: `cp node_modules/docsify/lib/themes/vue.css frontend/docs-viewer/vue.css`
3. Remove the Google Fonts `@import` from the first line of `vue.css` — it looks like:
   ```
   @import url("https://fonts.googleapis.com/css?family=...");
   ```
   The rest of the file can be left as-is.
4. Commit `vue.css` alongside the `package.json` / `package-lock.json` changes.

The font is intentionally replaced with the system font stack via a `<style>` block in
`src/main/resources/docs-index.html`, so no external network requests are made at runtime.
