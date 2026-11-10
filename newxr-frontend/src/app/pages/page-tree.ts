import { ChangeDetectionStrategy, Component, effect, input, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Icon } from '../shared/ui';
import { TreeNode } from './pages-api';

/** Recursive, collapsible page tree for the space sidebar. */
@Component({
  selector: 'nx-page-tree',
  imports: [RouterLink, RouterLinkActive, Icon, PageTree],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`.node{display:flex;align-items:center;gap:4px;height:28px;padding-right:8px;border-radius:6px;font-size:13px;color:var(--text);text-decoration:none!important}
    .node:hover{background:var(--surface-hover)} .node.active{background:var(--surface-selected);color:var(--accent-hover);font-weight:600}
    .tw{width:18px;height:18px;border:0;background:none;color:var(--text-subtle);display:inline-flex;align-items:center;justify-content:center;cursor:pointer;border-radius:4px;flex:none}
    .tw:hover{background:var(--surface-hover)} .ic{color:var(--text-muted);flex:none} .draft{font-size:10px;color:var(--text-subtle);margin-left:auto}`],
  template: `@for (n of nodes(); track n.id) {
      <a class="node" [routerLink]="['/pages', space(), n.id]" routerLinkActive="active" [style.padding-left.px]="depth() * 14 + 2">
        @if (n.children.length) { <button class="tw" (click)="$event.preventDefault(); $event.stopPropagation(); toggle(n.id)"><nx-icon [name]="open().has(n.id) ? 'chevron-down' : 'chevron-right'" [size]="12" /></button> }
        @else { <span class="tw"></span> }
        <nx-icon name="page" [size]="13" class="ic" /><span class="truncate">{{ n.title }}</span>
        @if (n.status === 'DRAFT') { <span class="draft">draft</span> }
      </a>
      @if (n.children.length && open().has(n.id)) { <nx-page-tree [nodes]="n.children" [space]="space()" [depth]="depth() + 1" /> }
    }`,
})
export class PageTree {
  readonly nodes = input<TreeNode[]>([]);
  readonly space = input.required<string>();
  readonly depth = input(0);
  readonly open = signal(new Set<number>());
  private expanded = false;
  constructor() {
    // Expand the top level once, when the tree first arrives.
    effect(() => {
      const nodes = this.nodes();
      if (!this.expanded && this.depth() === 0 && nodes.length) {
        this.expanded = true;
        this.open.set(new Set(nodes.filter(n => n.children.length).map(n => n.id)));
      }
    });
  }
  toggle(id: number) { const s = new Set(this.open()); s.has(id) ? s.delete(id) : s.add(id); this.open.set(s); }
}
