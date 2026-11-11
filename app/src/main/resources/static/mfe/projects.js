// The projects micro frontend: <hl-projects>, mounted by the homelabs app shell.
// It renders into its own shadow root with the shell's shared styles, reads data only
// through context.api (paths relative to this service's /api), sets the breadcrumb
// and navigates through the context. Read views; nothing here writes.
//
// Routes below the MFE's base path:
//   /                      project list
//   /<CODE>                project page, plan tab
//   /<CODE>/<tab>          project page, comments | activity | attachments tab
//   /<CODE>/<REF>          work package or task page

import { renderList } from './views/list.js';
import { renderProject, TABS } from './views/project.js';
import { renderItem } from './views/item.js';
import { texts } from './lib.js';

const REFERENCE = /^[A-Z][A-Z0-9]*-\d+$/;
const CODE = /^[A-Z][A-Z0-9]*$/;

// Layout of this MFE's own content only — colours, type and sizes come from the
// shell's tokens and ui.css.
const LAYOUT = new CSSStyleSheet();
LAYOUT.replaceSync(`
  .stack > * + * { margin-top: var(--space-4); }
  .stack--tight > * + * { margin-top: var(--space-2); }
  .page-meta { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); }
  .split { display: grid; grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr); gap: var(--space-6); align-items: start; }
  @media (max-width: 900px) { .split { grid-template-columns: minmax(0, 1fr); } }
  .toolbar .search { flex: 0 1 auto; }
  .toolbar .select { width: auto; min-width: 180px; }
  .description { margin-bottom: var(--space-6); }
  .table td .tree-toggle + .ref, .table td .tree-spacer + .ref { margin-left: var(--space-1); }
  .tree-spacer { display: inline-block; width: 24px; }
  .group-row td { color: var(--color-text-muted); font-weight: var(--weight-medium); }
`);

class ProjectsMfe extends HTMLElement {
  #context = null;
  #render = 0;

  /** View state that outlives a navigation inside the MFE (filters, folded rows). */
  state = { filter: { status: 'active', q: '' }, collapsed: new Set() };

  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
  }

  set context(context) {
    this.#context = context;
    this.shadowRoot.adoptedStyleSheets = [...context.styles, LAYOUT];
    this.render();
  }

  get context() {
    return this.#context;
  }

  /** Re-renders the current route; an older render that finishes late is dropped. */
  render() {
    const ticket = ++this.#render;
    const view = {
      ctx: this.#context,
      text: texts(this.#context.locale),
      state: this.state,
      root: this.shadowRoot,
      current: () => ticket === this.#render,
      show: (node) => {
        if (ticket === this.#render) this.shadowRoot.replaceChildren(node);
      },
      rerender: () => this.render(),
    };
    const [code, second, ...rest] = this.#context.path.split('/').filter(Boolean).map(decodeURIComponent);

    if (!code) return renderList(view);
    if (!CODE.test(code) || rest.length) return renderItem(view, null, null);
    if (!second) return renderProject(view, code, 'plan');
    if (TABS.includes(second)) return renderProject(view, code, second);
    if (REFERENCE.test(second)) return renderItem(view, code, second);
    return renderItem(view, null, null);
  }
}

if (!customElements.get('hl-projects')) customElements.define('hl-projects', ProjectsMfe);
