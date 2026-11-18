# Frontend

The selected frontend stack is React, TypeScript and Vite. Browser code belongs in `src/`.

The intended interface supports exploring issues, meetings and individual records, following issue evolution and inspecting source evidence. It consumes application APIs through the routing boundary.

## Run locally

Use Node.js 26 LTS and npm.

```sh
cd frontend
npm ci
npm run dev
```

Open the localhost address Vite prints. The app provides all seven Ledger screens: home, search, issue timeline, meeting, record, browse issues and browse meetings. It uses typed sample data only.

```sh
npm run build
npm run preview
```

The build type-checks strict TypeScript before producing `dist/`. Preview serves that build locally. Stop either server with Ctrl+C when finished.

Screen ownership, API signatures and shared components are documented in [src/screens/README.md](src/screens/README.md). Inter 4.1 and its OFL licence are self-hosted under `public/fonts/`. The page starts in the reader's system light or dark setting; the theme toggle at the top right of the header switches it, and the choice is kept in that browser.
