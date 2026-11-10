/**
 * Small Markdown renderer for descriptions, comments and pages. Input is HTML-escaped first, so
 * the output only contains the tags produced here; Angular's sanitizer runs on top of it.
 * Supports headings, bold/italic/strike, inline and fenced code, links, lists, task lists,
 * block quotes, info panels (> [!info] / [!warning] / [!success]) and pipe tables.
 */
export function markdown(src: string | null | undefined): string {
  if (!src) return '';
  const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const inline = (s: string) => esc(s)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>')
    .replace(/~~([^~]+)~~/g, '<del>$1</del>')
    .replace(/\[([^\]]+)\]\(((?:https?:\/\/|\/)[^)\s]*)\)/g, (_m, t, u) => `<a href="${u}"${u.startsWith('/') ? '' : ' target="_blank" rel="noopener"'}>${t}</a>`)
    .replace(/(^|\s)@([A-Z][a-z]+(?: [A-Z][a-z]+)?)/g, '$1<span class="mention">@$2</span>');

  const lines = src.replace(/\r\n/g, '\n').split('\n');
  const out: string[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (/^```/.test(line)) {
      const buf: string[] = [];
      i++;
      while (i < lines.length && !/^```/.test(lines[i])) buf.push(lines[i++]);
      i++;
      out.push(`<pre><code>${esc(buf.join('\n'))}</code></pre>`);
      continue;
    }
    const h = /^(#{1,4})\s+(.*)$/.exec(line);
    if (h) { out.push(`<h${h[1].length}>${inline(h[2])}</h${h[1].length}>`); i++; continue; }
    if (/^\s*(---|\*\*\*)\s*$/.test(line)) { out.push('<hr>'); i++; continue; }
    if (/^>\s?\[!(info|warning|success|note)\]/i.test(line)) {
      const kind = /\[!(\w+)\]/.exec(line)![1].toLowerCase();
      const buf = [line.replace(/^>\s?\[!\w+\]\s*/, '')];
      i++;
      while (i < lines.length && /^>/.test(lines[i])) buf.push(lines[i++].replace(/^>\s?/, ''));
      out.push(`<div class="panel ${kind}">${buf.filter(Boolean).map(inline).join('<br>')}</div>`);
      continue;
    }
    if (/^>/.test(line)) {
      const buf: string[] = [];
      while (i < lines.length && /^>/.test(lines[i])) buf.push(lines[i++].replace(/^>\s?/, ''));
      out.push(`<blockquote>${buf.map(inline).join('<br>')}</blockquote>`);
      continue;
    }
    if (/^\s*\|.*\|\s*$/.test(line) && i + 1 < lines.length && /^\s*\|?[\s:-]+\|/.test(lines[i + 1])) {
      const cells = (l: string) => l.trim().replace(/^\||\|$/g, '').split('|').map(c => c.trim());
      const head = cells(line);
      i += 2;
      const rows: string[][] = [];
      while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) rows.push(cells(lines[i++]));
      out.push('<table><thead><tr>' + head.map(c => `<th>${inline(c)}</th>`).join('') + '</tr></thead><tbody>'
        + rows.map(r => '<tr>' + r.map(c => `<td>${inline(c)}</td>`).join('') + '</tr>').join('') + '</tbody></table>');
      continue;
    }
    if (/^\s*([-*]|\d+\.)\s+/.test(line)) {
      const ordered = /^\s*\d+\./.test(line);
      const items: string[] = [];
      while (i < lines.length && /^\s*([-*]|\d+\.)\s+/.test(lines[i])) {
        const t = lines[i++].replace(/^\s*([-*]|\d+\.)\s+/, '');
        const task = /^\[( |x)\]\s*(.*)$/i.exec(t);
        items.push(task ? `<li class="task"><span class="box${task[1].toLowerCase() === 'x' ? ' done' : ''}">${task[1].toLowerCase() === 'x' ? '✓' : ''}</span>${inline(task[2])}</li>` : `<li>${inline(t)}</li>`);
      }
      out.push(`<${ordered ? 'ol' : 'ul'}>${items.join('')}</${ordered ? 'ol' : 'ul'}>`);
      continue;
    }
    if (!line.trim()) { i++; continue; }
    const buf: string[] = [];
    while (i < lines.length && lines[i].trim() && !/^(#{1,4}\s|```|>|\s*([-*]|\d+\.)\s|\s*\|)/.test(lines[i])) buf.push(lines[i++]);
    out.push(`<p>${buf.map(inline).join('<br>')}</p>`);
  }
  return out.join('\n');
}
