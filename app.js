'use strict';

const search = document.querySelector('#doc-search');
const sections = [...document.querySelectorAll('.doc-section')];
const links = [...document.querySelectorAll('#toc a')];
const status = document.querySelector('#search-status');
const menu = document.querySelector('#menu-button');
const sidebar = document.querySelector('#sidebar');
const searchable = sections.map(section => ({ section, text: section.textContent.toLowerCase() }));

function closeMenu() {
  sidebar.classList.remove('is-open');
  menu.setAttribute('aria-expanded', 'false');
}

menu.addEventListener('click', () => {
  const open = sidebar.classList.toggle('is-open');
  menu.setAttribute('aria-expanded', String(open));
  if (open) search.focus();
});

document.addEventListener('keydown', event => {
  if (event.key === 'Escape') closeMenu();
});
document.addEventListener('click', event => {
  if (window.innerWidth <= 760 && !sidebar.contains(event.target) && !menu.contains(event.target)) closeMenu();
});

search.addEventListener('input', () => {
  const terms = search.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
  let shown = 0;
  searchable.forEach(({ section, text }) => {
    const match = terms.every(term => text.includes(term));
    section.hidden = !match;
    if (match) shown++;
  });
  links.forEach(link => {
    const target = document.getElementById(link.hash.slice(1));
    link.hidden = Boolean(target.closest('.doc-section')?.hidden);
  });
  document.querySelector('#no-results').hidden = shown !== 0;
  status.textContent = terms.length ? `找到 ${shown} 个相关章节 · 清空搜索显示全部` : '13 个章节 · 7 项接口';
});

links.forEach(link => link.addEventListener('click', closeMenu));

// Section links remain useful without JavaScript; this only adds active tracking.
if ('IntersectionObserver' in window) {
  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      links.forEach(link => {
        const active = link.hash === '#' + entry.target.id;
        link.classList.toggle('active', active);
        if (active) link.setAttribute('aria-current', 'location');
        else link.removeAttribute('aria-current');
      });
    });
  }, { rootMargin: '-100px 0px -65% 0px', threshold: 0 });
  document.querySelectorAll('.doc-section h2, .doc-section h3').forEach(h => observer.observe(h));
}

async function copyCode(button) {
  const code = button.closest('.code-body').querySelector('code').textContent;
  try {
    await navigator.clipboard.writeText(code);
    button.textContent = '已复制';
    setTimeout(() => { button.textContent = '复制'; }, 1800);
  } catch {
    const selection = window.getSelection();
    const range = document.createRange();
    range.selectNodeContents(button.closest('.code-body').querySelector('code'));
    selection.removeAllRanges();
    selection.addRange(range);
    button.textContent = '请按 Ctrl/Cmd+C';
  }
}
document.querySelectorAll('.copy-button').forEach(button => button.addEventListener('click', () => copyCode(button)));

function showAllForPrint() {
  search.value = '';
  search.dispatchEvent(new Event('input'));
  document.querySelectorAll('.code-example').forEach(example => { example.open = true; });
}
document.querySelector('#print-button').addEventListener('click', () => { showAllForPrint(); window.print(); });
window.addEventListener('beforeprint', showAllForPrint);

// A shared anchor must stay reachable even after a local search.
window.addEventListener('hashchange', () => {
  const target = document.getElementById(location.hash.slice(1));
  if (target?.closest('.doc-section')?.hidden) {
    search.value = '';
    search.dispatchEvent(new Event('input'));
    target.scrollIntoView();
  }
});
