// Shared helpers of the projects micro frontend: DOM building without innerHTML,
// the sprite icons of the shell, texts in German and English, formatting, and the
// page states. Nothing here knows about a particular view.

const SVG = 'http://www.w3.org/2000/svg';

/** Element factory: attributes (on* = listener), then children (strings become text). */
export function h(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value === null || value === undefined || value === false) continue;
    if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children.flat(Infinity)) {
    if (child !== null && child !== undefined && child !== false) node.append(child);
  }
  return node;
}

/** An icon from the shell's sprite (unknown names render as the generic icon there). */
export function icon(name) {
  const svg = document.createElementNS(SVG, 'svg');
  svg.setAttribute('class', 'icon');
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS(SVG, 'use');
  use.setAttribute('href', `/shell/icons.svg#${name}`);
  svg.append(use);
  return svg;
}

// ---------- Texts ------------------------------------------------------------------

const TEXT = {
  de: {
    projects: 'Projekte', project: 'Projekt', searchPlaceholder: 'Projekte durchsuchen …',
    filterStatus: 'Status', allStatuses: 'Alle Status', count: (n) => (n === 1 ? '1 Projekt' : `${n} Projekte`),
    name: 'Name', status: 'Status', labels: 'Labels', owner: 'Verantwortlich', updated: 'Aktualisiert',
    created: 'Angelegt', title: 'Titel', assignee: 'Zugewiesen', due: 'Fällig', workPackage: 'Arbeitspaket',
    task: 'Aufgabe', tabPlan: 'Plan', tabComments: 'Kommentare', tabActivity: 'Aktivität', tabAttachments: 'Anhänge',
    ungrouped: 'Ohne Arbeitspaket', toggle: 'Auf- oder zuklappen', description: 'Beschreibung', comments: 'Kommentare',
    file: 'Datei', type: 'Typ', uploadedBy: 'Hochgeladen von', truncated: (n) => `Es werden die ersten ${n} Einträge gezeigt.`,
    noProjects: 'Keine Projekte', noProjectsText: 'Kein Projekt passt zu diesem Filter.',
    noPlan: 'Noch kein Plan', noPlanText: 'Dieses Projekt hat noch keine Arbeitspakete oder Aufgaben.',
    noComments: 'Keine Kommentare', noActivity: 'Keine Aktivität', noAttachments: 'Keine Anhänge',
    notFound: 'Nicht gefunden', notFoundText: 'Diesen Eintrag gibt es nicht oder nicht in diesem Projekt.',
    loadError: 'Laden fehlgeschlagen', retry: 'Erneut versuchen', noDescription: 'Keine Beschreibung.',
    via: 'über',
  },
  en: {
    projects: 'Projects', project: 'Project', searchPlaceholder: 'Search projects …',
    filterStatus: 'Status', allStatuses: 'All statuses', count: (n) => (n === 1 ? '1 project' : `${n} projects`),
    name: 'Name', status: 'Status', labels: 'Labels', owner: 'Owner', updated: 'Updated',
    created: 'Created', title: 'Title', assignee: 'Assignee', due: 'Due', workPackage: 'Work package',
    task: 'Task', tabPlan: 'Plan', tabComments: 'Comments', tabActivity: 'Activity', tabAttachments: 'Attachments',
    ungrouped: 'Without work package', toggle: 'Expand or collapse', description: 'Description', comments: 'Comments',
    file: 'File', type: 'Type', uploadedBy: 'Uploaded by', truncated: (n) => `Showing the first ${n} entries.`,
    noProjects: 'No projects', noProjectsText: 'No project matches this filter.',
    noPlan: 'No plan yet', noPlanText: 'This project has no work packages or tasks yet.',
    noComments: 'No comments', noActivity: 'No activity', noAttachments: 'No attachments',
    notFound: 'Not found', notFoundText: 'This entry does not exist, or not in this project.',
    loadError: 'Loading failed', retry: 'Try again', noDescription: 'No description.',
    via: 'via',
  },
};

const PROJECT_STATUS = {
  de: { active: 'Aktiv', deactivated: 'Deaktiviert', archived: 'Archiviert' },
  en: { active: 'Active', deactivated: 'Deactivated', archived: 'Archived' },
};

const ITEM_STATUS = {
  de: { todo: 'Offen', in_progress: 'In Arbeit', blocked: 'Blockiert', done: 'Erledigt', cancelled: 'Abgebrochen' },
  en: { todo: 'Open', in_progress: 'In progress', blocked: 'Blocked', done: 'Done', cancelled: 'Cancelled' },
};

const EVENTS = {
  de: { created: 'hat angelegt', updated: 'hat geändert', status_changed: 'hat den Status geändert', labeled: 'hat Labels gesetzt',
    unlabeled: 'hat Labels entfernt', commented: 'hat kommentiert', attached: 'hat eine Datei angehängt', detached: 'hat einen Anhang entfernt' },
  en: { created: 'created', updated: 'changed', status_changed: 'changed the status', labeled: 'added labels',
    unlabeled: 'removed labels', commented: 'commented', attached: 'attached a file', detached: 'removed an attachment' },
};

const PILL = {
  active: 'pill pill--success', deactivated: 'pill pill--muted', archived: 'pill pill--muted',
  todo: 'pill', in_progress: 'pill pill--progress', blocked: 'pill pill--warning', done: 'pill pill--success',
  cancelled: 'pill pill--muted',
};

export const PROJECT_STATUSES = ['active', 'deactivated', 'archived'];

export function texts(locale) {
  return TEXT[locale] ?? TEXT.de;
}

export function projectPill(status, locale) {
  return h('span', { class: PILL[status] ?? 'pill' }, (PROJECT_STATUS[locale] ?? PROJECT_STATUS.de)[status] ?? status);
}

export function itemPill(status, locale) {
  return h('span', { class: PILL[status] ?? 'pill' }, (ITEM_STATUS[locale] ?? ITEM_STATUS.de)[status] ?? status);
}

export function projectStatusLabel(status, locale) {
  return (PROJECT_STATUS[locale] ?? PROJECT_STATUS.de)[status] ?? status;
}

export function eventText(eventType, locale) {
  return (EVENTS[locale] ?? EVENTS.de)[eventType] ?? eventType;
}

/** An activity's detail; a status change reads in the same words as the pills. */
export function eventDetail(activity, locale) {
  if (activity.event_type !== 'status_changed') return activity.detail;
  const names = activity.entity_type === 'project' ? (PROJECT_STATUS[locale] ?? PROJECT_STATUS.de)
    : (ITEM_STATUS[locale] ?? ITEM_STATUS.de);
  return activity.detail.split(' → ').map((status) => names[status.trim()] ?? status).join(' → ');
}

// ---------- Formatting -------------------------------------------------------------

/** A calendar date (yyyy-mm-dd from the API) in the reader's language. */
export function formatDate(isoDate, locale) {
  if (!isoDate) return null;
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Intl.DateTimeFormat(locale, { day: '2-digit', month: '2-digit', year: 'numeric' })
    .format(new Date(year, month - 1, day));
}

/** A point in time: relative while recent, a date after a week. */
export function formatTime(instant, locale) {
  if (!instant) return null;
  const date = new Date(instant);
  const seconds = Math.round((date.getTime() - Date.now()) / 1000);
  const relative = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
  const abs = Math.abs(seconds);
  if (abs < 60) return relative.format(seconds, 'second');
  if (abs < 3600) return relative.format(Math.round(seconds / 60), 'minute');
  if (abs < 86400) return relative.format(Math.round(seconds / 3600), 'hour');
  if (abs < 7 * 86400) return relative.format(Math.round(seconds / 86400), 'day');
  return new Intl.DateTimeFormat(locale, { day: '2-digit', month: '2-digit', year: 'numeric' }).format(date);
}

/** The full timestamp, for a title attribute next to a relative time. */
export function exactTime(instant, locale) {
  return instant ? new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(instant)) : null;
}

export function initials(name) {
  const parts = String(name ?? '').split(/[\s._-]+/).filter(Boolean);
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase() || '?';
}

/** A person by name — never a raw subject id; nobody renders as a quiet dash. */
export function person(name) {
  if (!name) return h('span', { class: 'subtle' }, '—');
  return h('span', { class: 'person' }, h('span', { class: 'avatar' }, initials(name)), name);
}

export function dash(value) {
  return value ?? h('span', { class: 'subtle' }, '—');
}

export function labelList(labels) {
  const names = (labels ?? []).map((label) => (typeof label === 'string' ? label : label.name));
  return names.length ? h('span', { class: 'labels' }, names.map((name) => h('span', { class: 'label' }, name))) : null;
}

/**
 * The service's sanitized rich text, rendered inside the shadow root. Defence in depth
 * on top of the service's allow-list: no scripts, styles or handlers survive; API links
 * and images are re-addressed through the app's proxy; outside links open apart.
 */
export function richText(html, api) {
  const template = document.createElement('template');
  template.innerHTML = html ?? '';
  const content = template.content;
  content.querySelectorAll('script, style, iframe, object, embed, link, meta, form').forEach((node) => node.remove());
  content.querySelectorAll('*').forEach((node) => {
    for (const attribute of [...node.attributes]) {
      if (attribute.name.startsWith('on') || attribute.name === 'style' || attribute.name === 'class') {
        node.removeAttribute(attribute.name);
      }
    }
    for (const name of ['href', 'src']) {
      const value = node.getAttribute(name);
      if (value === null) continue;
      if (value.startsWith('/api/')) node.setAttribute(name, api.url(value.slice(4)));
      else if (!/^(https?:|mailto:)/i.test(value)) node.removeAttribute(name);
    }
    if (node.tagName === 'A' && /^https?:/i.test(node.getAttribute('href') ?? '')) {
      node.setAttribute('target', '_blank');
      node.setAttribute('rel', 'noopener noreferrer');
    }
  });
  return h('div', { class: 'prose' }, content);
}

// ---------- States -----------------------------------------------------------------

export function loading() {
  return h('div', { class: 'page', 'aria-busy': 'true' },
    h('div', { class: 'skeleton' }),
    h('div', { class: 'skeleton' }), h('div', { class: 'skeleton' }), h('div', { class: 'skeleton' }));
}

export function empty(title, text) {
  return h('div', { class: 'empty' }, h('p', { class: 'empty__title' }, title), text ? h('p', {}, text) : null);
}

/** A failed load: what the service said, verbatim, and a way to try again. */
export function failure(error, text, retry) {
  const said = error?.body?.error ?? error?.message ?? String(error);
  return h('div', { class: 'notice notice--danger', role: 'alert' }, icon('alert'),
    h('div', {}, text.loadError, h('div', { class: 'notice__detail' }, said),
      retry ? h('button', { class: 'btn btn--sm', type: 'button', onclick: retry }, text.retry) : null));
}

export function isNotFound(error) {
  return error?.status === 404;
}

/** Comments and activity: a feed, newest first. */
export function feed(ctx, entries, emptyTitle) {
  if (!entries.length) return h('div', { class: 'card' }, empty(emptyTitle));
  const sorted = [...entries].sort((a, b) => String(b.when).localeCompare(String(a.when)));
  return h('div', { class: 'card' }, h('div', { class: 'card__body' }, h('ul', { class: 'feed' }, sorted.map((entry) =>
    h('li', { class: 'feed__item' }, h('span', { class: 'avatar' }, initials(entry.who)), h('div', {},
      h('div', { class: 'feed__meta' }, h('span', { class: 'feed__author' }, entry.who ?? '—'),
        h('span', { class: 'feed__time', title: exactTime(entry.when, ctx.locale) }, formatTime(entry.when, ctx.locale))),
      h('p', { class: 'feed__text' }, entry.what)))))));
}

/** The page for a route that names nothing in this project. */
export function notFound(text) {
  return h('div', { class: 'page' }, h('div', { class: 'card' }, empty(text.notFound, text.notFoundText)));
}
