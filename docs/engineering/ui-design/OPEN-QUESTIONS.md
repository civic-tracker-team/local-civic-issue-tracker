# UI design open questions

Questions the screen designs raised that are not settled yet. Write your answer under **Answer:**. Answered questions move to `DECISIONS.md` beside this file.

## Before signing off direction a

### 1. Should the browse-issues screen filter by issue category?

SRS v2.0 includes categorization only if it stays simple within the existing analysis work (FR-39, FR-40), so it may not exist. 6a filters by name and last activity and sorts by activity, number of meetings or name, none of which need categories. Design 6b also shows a category filter marked "if categorization is included" so the team can see what it would add.

Options to start from:
- **No category filter until FR-39 is confirmed.** Keeps the screen within settled scope. (Recommended: the designs work without it, and adding a filter later is easy.)
- **Design for categories now.** Shows a category filter on every design, on the assumption that categorization will be included.

**Answer:**

### 2. Should home (1a) take the search callout from 1c?

A teammate liked the large search box at the top of 1c, and the design review also leaned to 1c for the home screen. 1a already leads with search, but more quietly.

Options to start from:
- **Bring the 1c search callout into 1a, in a's style.** Keeps one look while using the part people liked. (Recommended: the only part of c the team asked for.)
- **Keep 1a as it is.**

**Answer:**

### 3. Should the issue timeline (3a) add the compact timeline strip from 3c?

The review suggested it: a horizontal strip of meetings above the dated list gives an overview of a long issue at a glance.

Options to start from:
- **Add the strip above 3a's list.** (Recommended: the parking structure issue has nine meetings, and the strip shows the span before the reader scrolls.)
- **Keep 3a as a list only.**

**Answer:**

### 4. Should the record view (5a) put connection notes beside each passage on desktop, as 5d does?

The review leaned to 5d on desktop because each note sits next to the passage it explains. 5a lists the connections in a side panel.

Options to start from:
- **Keep 5a's side panel.** Simpler, and it already works on phone. (Recommended: the team found d's look low effort, and a's panel covers the same requirement.)
- **Move the notes beside each passage on desktop only.**

**Answer:**

### 5. Should the chosen a designs move into version control?

DECISIONS.md keeps draft design source in the ignored `.local/design/` folder "until a design is chosen". Direction a is now chosen, and the frontend will be built from it.

Options to start from:
- **Copy only 1a to 7a, their phone previews and `canvas.json` into `docs/engineering/ui-design/designs/` after sign-off.** The team can see the spec the frontend follows. (Recommended: small, and it is the reference for phase 2.)
- **Keep everything in `.local`; the public canvas link is the reference.**

**Answer:**

## Before building the frontend

`frontend/README.md` leaves package versions, UI libraries, routes, data fetching and styling undecided. These need answers before any package is added.

### 6. How should the frontend be styled?

Options to start from:
- **Plain CSS with custom properties for a's tokens (colors, type, spacing), light and dark.** No new dependency. (Recommended: a's designs are already expressed as a small token set.)
- **A utility CSS framework such as Tailwind.**
- **A component library.** Brings its own look, which would fight direction a.

**Answer:**

### 7. Which routing library should the frontend use?

Seven screens need their own addresses so links to an issue, meeting or record can be shared.

Options to start from:
- **React Router.** The most common choice for a React and Vite app. (Recommended: widely documented for a student team.)
- **TanStack Router.** Typed routes, but less familiar.

**Answer:**

### 8. Where do the screens get data while most APIs do not exist yet?

Only the records service has read endpoints so far; search and analysis have no routes.

Options to start from:
- **Typed sample data behind a small API-client layer,** swapped for real calls as each service adds endpoints. (Recommended: the screens can be built and reviewed now, and the sample data already exists in the designs.)
- **Wait for each service's API before building its screens.**

**Answer:**

### 9. Which package manager and Node version should the frontend use?

Options to start from:
- **npm with a committed lockfile, on the current Node LTS.** (Recommended: ships with Node, so no extra tool for teammates.)
- **pnpm.**

**Answer:**
