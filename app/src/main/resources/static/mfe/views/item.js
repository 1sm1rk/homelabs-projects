// The item page: one work package or task — full record, description, comments.
// A reference names either; the page asks for a work package first, then a task, and
// shows "not found" for an item of another project (a hand-typed URL).

import { h, itemPill, person, labelList, formatDate, formatTime, exactTime, richText, loading, failure,
  isNotFound, dash, feed, notFound } from '../lib.js';

export async function renderItem(view, code, ref) {
  const { ctx, text } = view;
  if (!code || !ref) {
    ctx.setTrail([]);
    view.show(notFound(text));
    return;
  }
  ctx.setTrail([{ label: code, path: `/${code}` }, { label: ref }]);
  view.show(loading());

  let item;
  let kind;
  let comments;
  try {
    [item, kind] = await find(ctx, ref);
    comments = item ? await ctx.api.get(`/comments?parent=${encodeURIComponent(`${kind}:${ref}`)}`) : [];
  } catch (error) {
    view.show(h('div', { class: 'page' }, failure(error, text, view.rerender)));
    return;
  }
  if (!view.current()) return;
  if (!item || item.project_short_code !== code) {
    view.show(notFound(text));
    return;
  }

  view.show(h('div', { class: 'page' },
    h('header', { class: 'page-header' },
      h('div', { class: 'page-header__main' },
        h('div', { class: 'page-meta' }, h('span', { class: 'ref' }, item.reference),
          h('span', { class: 'subtle' }, kind === 'task' ? text.task : text.workPackage),
          itemPill(item.status, ctx.locale), labelList(item.labels)),
        h('h1', { class: 'page-title' }, item.title))),
    h('div', { class: 'split' },
      h('div', { class: 'stack' },
        h('section', { class: 'stack--tight' }, h('h2', { class: 'section-title' }, text.description),
          item.description_html ? richText(item.description_html, ctx.api) : h('p', { class: 'subtle' }, text.noDescription)),
        h('section', { class: 'stack--tight' }, h('h2', { class: 'section-title' }, text.comments),
          feed(ctx, comments.map((c) => ({ who: c.author_name, when: c.created_at, what: c.content })), text.noComments))),
      h('div', { class: 'card' }, h('div', { class: 'card__body' }, record(ctx, text, code, item, kind))))));
}

async function find(ctx, ref) {
  const path = encodeURIComponent(ref);
  try {
    return [await ctx.api.get(`/work-packages/${path}`), 'work_package'];
  } catch (error) {
    if (!isNotFound(error)) throw error;
  }
  try {
    return [await ctx.api.get(`/tasks/${path}`), 'task'];
  } catch (error) {
    if (!isNotFound(error)) throw error;
  }
  return [null, null];
}

function record(ctx, text, code, item, kind) {
  const row = (term, value) => [h('dt', {}, term), h('dd', {}, value)];
  return h('dl', { class: 'record' },
    row(text.status, itemPill(item.status, ctx.locale)),
    row(text.assignee, person(item.assignee_name)),
    row(text.due, dash(formatDate(item.due_date, ctx.locale))),
    kind === 'task' ? row(text.workPackage, item.work_package_reference
      ? h('a', { href: ctx.href(`/${code}/${item.work_package_reference}`) }, item.work_package_reference)
      : dash(null)) : null,
    row(text.labels, labelList(item.labels) ?? dash(null)),
    row(text.created, h('span', { title: exactTime(item.created_at, ctx.locale) }, formatTime(item.created_at, ctx.locale))),
    row(text.updated, h('span', { title: exactTime(item.updated_at, ctx.locale) }, formatTime(item.updated_at, ctx.locale))));
}
