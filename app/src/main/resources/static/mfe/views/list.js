// The project list: reference, name, status, labels, owner, last change — filtered by
// status and text. A row opens the project page.

import { h, icon, projectPill, projectStatusLabel, PROJECT_STATUSES, person, labelList, formatTime, exactTime,
  loading, empty, failure } from '../lib.js';

const LIMIT = 100;
let debounce = null;

export async function renderList(view) {
  const { ctx, text, state } = view;
  ctx.setTrail([]);
  if (!view.root.querySelector('[data-view="list"]')) view.show(loading());

  const query = new URLSearchParams({ limit: String(LIMIT) });
  if (state.filter.status) query.append('status', state.filter.status);
  if (state.filter.q.trim()) query.set('q', state.filter.q.trim());

  let projects;
  try {
    projects = await ctx.api.get(`/projects?${query}`);
  } catch (error) {
    view.show(h('div', { class: 'page' }, failure(error, text, view.rerender)));
    return;
  }
  if (!view.current()) return;

  const hadFocus = view.root.activeElement?.dataset?.focus;
  view.show(page(view, projects));
  if (hadFocus) {
    const input = view.root.querySelector(`[data-focus="${hadFocus}"]`);
    input?.focus();
    input?.setSelectionRange?.(input.value.length, input.value.length);
  }
}

function page(view, projects) {
  const { ctx, text, state } = view;
  return h('div', { class: 'page', 'data-view': 'list' },
    h('header', { class: 'page-header' },
      h('div', { class: 'page-header__main' },
        h('h1', { class: 'page-title' }, text.projects),
        h('div', { class: 'page-meta' }, text.count(projects.length)))),
    toolbar(view),
    projects.length
      ? h('div', { class: 'card' }, h('table', { class: 'table' },
        h('thead', {}, h('tr', {},
          h('th', {}, text.name), h('th', {}, text.status), h('th', {}, text.labels),
          h('th', {}, text.owner), h('th', { class: 'cell-num' }, text.updated))),
        h('tbody', {}, projects.map((project) => row(ctx, project)))))
      : h('div', { class: 'card' }, empty(text.noProjects, text.noProjectsText)),
    projects.length === LIMIT ? h('p', { class: 'subtle' }, text.truncated(LIMIT)) : null);
}

function toolbar(view) {
  const { ctx, text, state } = view;
  const search = h('input', {
    class: 'input', type: 'search', placeholder: text.searchPlaceholder, value: state.filter.q,
    'aria-label': text.searchPlaceholder, 'data-focus': 'q',
    oninput: (event) => {
      state.filter.q = event.target.value;
      clearTimeout(debounce);
      debounce = setTimeout(view.rerender, 250);
    },
  });
  const status = h('select', {
    class: 'select', 'aria-label': text.filterStatus, 'data-focus': 'status',
    onchange: (event) => {
      state.filter.status = event.target.value;
      view.rerender();
    },
  },
  h('option', { value: '', selected: state.filter.status === '' }, text.allStatuses),
  PROJECT_STATUSES.map((value) => h('option', { value, selected: state.filter.status === value },
    projectStatusLabel(value, ctx.locale))));
  return h('div', { class: 'toolbar' }, h('div', { class: 'search' }, icon('search'), search), status);
}

function row(ctx, project) {
  const href = ctx.href(`/${project.reference}`);
  return h('tr', { class: 'is-clickable', onclick: (event) => {
    if (event.target.closest('a')) return;
    ctx.navigate(`/${project.reference}`);
  } },
  h('td', {}, h('span', { class: 'ref' }, project.reference), ' ', h('a', { class: 'cell-title', href }, project.name)),
  h('td', {}, projectPill(project.status, ctx.locale)),
  h('td', {}, labelList(project.labels)),
  h('td', {}, person(project.owner_name)),
  h('td', { class: 'cell-num subtle', title: exactTime(project.updated_at, ctx.locale) },
    formatTime(project.updated_at, ctx.locale)));
}
