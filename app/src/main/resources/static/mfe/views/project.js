// The project page: header, description and the tabs Plan / Comments / Activity /
// Attachments. The plan is the work-package → task tree in plan order; parents fold.

import { h, icon, projectPill, itemPill, person, labelList, formatDate, formatTime, exactTime, richText, eventText,
  eventDetail, loading, empty, failure, isNotFound, feed, notFound } from '../lib.js';

export const TABS = ['comments', 'activity', 'attachments'];

const LIMIT = 100;

export async function renderProject(view, code, tab) {
  const { ctx, text } = view;
  ctx.setTrail([{ label: code, path: `/${code}` }]);
  view.show(loading());

  let project;
  let content;
  try {
    [project, content] = await Promise.all([ctx.api.get(`/projects/${encodeURIComponent(code)}`), load(ctx, code, tab)]);
  } catch (error) {
    view.show(isNotFound(error) ? notFound(text) : h('div', { class: 'page' }, failure(error, text, view.rerender)));
    return;
  }
  if (!view.current()) return;

  view.show(h('div', { class: 'page' },
    header(ctx, text, project),
    project.description_html ? h('div', { class: 'description' }, richText(project.description_html, ctx.api)) : null,
    tabs(ctx, text, code, tab),
    body(view, project, tab, content)));
}

function load(ctx, code, tab) {
  const parent = encodeURIComponent(`project:${code}`);
  switch (tab) {
    case 'comments': return ctx.api.get(`/comments?parent=${parent}`);
    case 'activity': return ctx.api.get(`/activities?parent=${parent}`);
    case 'attachments': return ctx.api.get(`/attachments?parent=${parent}`);
    default: {
      const query = `project=${encodeURIComponent(code)}&include_inactive_projects=true&limit=${LIMIT}`;
      return Promise.all([ctx.api.get(`/work-packages?${query}`), ctx.api.get(`/tasks?${query}`)]);
    }
  }
}

function header(ctx, text, project) {
  return h('header', { class: 'page-header' },
    h('div', { class: 'page-header__main' },
      h('div', { class: 'page-meta' }, h('span', { class: 'ref' }, project.short_code),
        projectPill(project.status, ctx.locale), labelList(project.labels)),
      h('h1', { class: 'page-title' }, project.name),
      h('div', { class: 'page-meta' }, text.owner, person(project.owner_name), h('span', { class: 'subtle' }, '·'),
        h('span', { title: exactTime(project.updated_at, ctx.locale) },
          `${text.updated} ${formatTime(project.updated_at, ctx.locale)}`))));
}

function tabs(ctx, text, code, active) {
  const tab = (id, label) => h('button', {
    class: 'tab', role: 'tab', type: 'button', 'aria-selected': String(id === active),
    onclick: () => ctx.navigate(id === 'plan' ? `/${code}` : `/${code}/${id}`, { replace: true }),
  }, label);
  return h('div', { class: 'tabs', role: 'tablist' },
    tab('plan', text.tabPlan), tab('comments', text.tabComments),
    tab('activity', text.tabActivity), tab('attachments', text.tabAttachments));
}

function body(view, project, tab, content) {
  const { ctx, text } = view;
  switch (tab) {
    case 'comments': return feed(ctx, content.map((c) => ({ who: c.author_name, when: c.created_at, what: c.content })),
      text.noComments);
    case 'activity': return feed(ctx, content.map((a) => ({
      who: a.actor_name, when: a.created_at,
      what: [eventText(a.event_type, ctx.locale), a.detail ? `: ${eventDetail(a, ctx.locale)}` : '',
        a.source ? ` (${text.via} ${a.source})` : ''].join(''),
    })), text.noActivity);
    case 'attachments': return attachments(ctx, text, content);
    default: return plan(view, project, content);
  }
}

function attachments(ctx, text, list) {
  if (!list.length) return h('div', { class: 'card' }, empty(text.noAttachments));
  return h('div', { class: 'card' }, h('table', { class: 'table' },
    h('thead', {}, h('tr', {}, h('th', {}, text.file), h('th', {}, text.type), h('th', {}, text.uploadedBy),
      h('th', { class: 'cell-num' }, text.created))),
    h('tbody', {}, list.map((attachment) => h('tr', {},
      h('td', {}, h('a', { href: ctx.api.url(`/attachments/${attachment.id}/content`), target: '_blank', rel: 'noopener' },
        icon('paperclip'), ' ', attachment.filename)),
      h('td', { class: 'subtle mono' }, attachment.mime_type),
      h('td', {}, person(attachment.uploaded_by_name)),
      h('td', { class: 'cell-num subtle', title: exactTime(attachment.created_at, ctx.locale) },
        formatTime(attachment.created_at, ctx.locale)))))));
}

function plan(view, project, [workPackages, tasks]) {
  const { ctx, text, state } = view;
  if (!workPackages.length && !tasks.length) return h('div', { class: 'card' }, empty(text.noPlan, text.noPlanText));

  const byOrder = (a, b) => a.sort_order - b.sort_order || a.reference.localeCompare(b.reference, undefined, { numeric: true });
  const children = new Map();
  const ungrouped = [];
  for (const task of [...tasks].sort(byOrder)) {
    if (task.work_package_id && workPackages.some((wp) => wp.id === task.work_package_id)) {
      if (!children.has(task.work_package_id)) children.set(task.work_package_id, []);
      children.get(task.work_package_id).push(task);
    } else {
      ungrouped.push(task);
    }
  }

  const code = project.short_code;
  const rows = [];
  for (const wp of [...workPackages].sort(byOrder)) {
    const kids = children.get(wp.id) ?? [];
    const expanded = !state.collapsed.has(wp.id);
    rows.push(itemRow(ctx, wp, 'tree-parent', code, kids.length
      ? h('button', { class: 'tree-toggle', type: 'button', 'aria-expanded': String(expanded), 'aria-label': text.toggle,
        onclick: (event) => {
          event.stopPropagation();
          if (expanded) state.collapsed.add(wp.id); else state.collapsed.delete(wp.id);
          view.rerender();
        } }, icon('chevron-right'))
      : h('span', { class: 'tree-spacer' })));
    if (expanded) kids.forEach((task) => rows.push(itemRow(ctx, task, 'tree-child', code, null)));
  }
  if (ungrouped.length) {
    if (workPackages.length) rows.push(h('tr', { class: 'group-row' }, h('td', { colspan: '4' }, text.ungrouped)));
    ungrouped.forEach((task) => rows.push(itemRow(ctx, task, workPackages.length ? 'tree-child' : '', code, null)));
  }

  return h('div', { class: 'stack--tight' },
    h('div', { class: 'card' }, h('table', { class: 'table' },
      h('thead', {}, h('tr', {}, h('th', {}, text.title), h('th', {}, text.status), h('th', {}, text.assignee),
        h('th', { class: 'cell-num' }, text.due))),
      h('tbody', {}, rows))),
    workPackages.length === LIMIT || tasks.length === LIMIT ? h('p', { class: 'subtle' }, text.truncated(LIMIT)) : null);
}

function itemRow(ctx, item, kind, code, lead) {
  const path = `/${code}/${item.reference}`;
  return h('tr', { class: `${kind} is-clickable`.trim(), onclick: (event) => {
    if (event.target.closest('a, button')) return;
    ctx.navigate(path);
  } },
  h('td', {}, lead, h('span', { class: 'ref' }, item.reference), ' ',
    h('a', { class: kind === 'tree-parent' ? 'cell-title' : null, href: ctx.href(path) }, item.title)),
  h('td', {}, itemPill(item.status, ctx.locale)),
  h('td', {}, person(item.assignee_name)),
  h('td', { class: 'cell-num subtle' }, formatDate(item.due_date, ctx.locale) ?? '—'));
}
