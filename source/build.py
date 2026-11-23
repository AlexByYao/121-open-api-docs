"""Build a self-contained, allowlisted public API reference. No credentials required."""
import argparse
import html
import json
from pathlib import Path
import re
import shutil

from markdown_it import MarkdownIt

ROOT = Path(__file__).resolve().parent


def build(output: Path):
    source = (ROOT / 'content.md').read_text(encoding='utf-8')
    md = MarkdownIt('commonmark', {'html': False, 'linkify': False}).enable('table')
    tokens = md.parse(source)
    headings = []
    section_num = sub_num = 0
    for i, token in enumerate(tokens):
        if token.type == 'heading_open':
            title = tokens[i + 1].content
            if token.tag == 'h1':
                token.attrSet('id', 'top')
            elif token.tag == 'h2':
                section_num += 1
                sub_num = 0
                anchor = f'section-{section_num}'
                token.attrSet('id', anchor)
                headings.append((2, anchor, title))
            elif token.tag == 'h3':
                sub_num += 1
                anchor = f'section-{section_num}-{sub_num}'
                token.attrSet('id', anchor)
                # 章节内保留字段分组，只在侧栏展示主要章节，便于快速定位七个接口。

    def render_code(tokens, idx, options, env):
        token = tokens[idx]
        language = token.info.strip().split(' ')[0] or 'text'
        code = token.content.rstrip('\n')
        if language == 'json':
            json.loads(code)  # Fail publication on invalid JSON examples.
        lines = []
        # Highlight escaped JSON strings/numbers without processing HTML markup.
        for line in code.split('\n'):
            if language == 'json':
                pattern = r'"(?:[^"\\]|\\.)*"(?=\s*:)|"(?:[^"\\]|\\.)*"|\b(?:true|false|null|\d+(?:\.\d+)?)\b'
                parts, pos = [], 0
                for match in re.finditer(pattern, line):
                    parts.append(html.escape(line[pos:match.start()]))
                    value = match.group()
                    kind = 'key' if re.match(r'\s*:', line[match.end():]) else ('string' if value.startswith('"') else 'literal')
                    parts.append(f'<span class="token-{kind}">{html.escape(value)}</span>')
                    pos = match.end()
                parts.append(html.escape(line[pos:]))
                rendered = ''.join(parts)
            else:
                rendered = html.escape(line)
            lines.append('<span class="code-line">' + (rendered or ' ') + '</span>')
        return ('<details class="code-example" open><summary><span>' + html.escape(language.upper())
                + ' 示例</span><span class="toggle-label">展开 / 折叠源码</span></summary>'
                + '<div class="code-body"><button class="copy-button" type="button" aria-label="复制代码">复制</button>'
                + '<pre tabindex="0"><code class="language-' + html.escape(language, quote=True) + '">'
                + '\n'.join(lines) + '</code></pre></div></details>\n')

    md.renderer.rules['fence'] = render_code
    rendered = md.renderer.render(tokens, md.options, {})
    # A section is the unit used by the search and active navigation.
    rendered = re.sub(r'<h2 id="(section-\d+)">', r'</section><section class="doc-section" data-section="\1"><h2 id="\1">', rendered)
    intro, _, rest = rendered.partition('</section>')
    rendered = '<div class="document-intro">' + intro + '</div>' + rest + '</section>'
    rendered = rendered.replace('<table>', '<div class="table-scroll" tabindex="0"><table>').replace('</table>', '</table></div>')
    toc_links = []
    for level, anchor, title in headings:
        toc_links.append(f'<a class="toc-level-{level}" href="#{anchor}">{html.escape(title)}</a>')
    sidebar = '\n'.join(toc_links)
    overview = ''.join(f'<li><a href="#{anchor}">{html.escape(title)}</a></li>' for level, anchor, title in headings if level == 2)
    template = (ROOT / 'template.html').read_text(encoding='utf-8')
    page = template.replace('{{VERSION}}', re.search(r'版本 \*\*([^*]+)\*\*', source).group(1)).replace('{{CONTENT}}', rendered).replace('{{SIDEBAR}}', sidebar).replace('{{OVERVIEW}}', overview).replace('{{SECTION_COUNT}}', str(section_num))
    output.mkdir(parents=True, exist_ok=True)
    (output / 'index.html').write_text(page, encoding='utf-8', newline='\n')
    (output / '.nojekyll').write_text('', encoding='utf-8')
    (output / 'robots.txt').write_text('User-agent: *\nAllow: /\n', encoding='utf-8', newline='\n')
    for asset in ['styles.css', 'app.js']:
        shutil.copy2(ROOT / asset, output / asset)
    downloads = output / 'downloads'
    downloads.mkdir(exist_ok=True)
    source_specs = ROOT / 'specs'
    if not source_specs.exists():
        source_specs = ROOT.parent / 'docs'
    specs = {
        '智能化采购同步.openapi.yaml': 'openapi.yaml',
        '智能化采购同步.openapi.json': 'openapi.json',
        'U01-查询121用户信息接口.openapi.yaml': 'u01.openapi.yaml',
    }
    for original, public in specs.items():
        shutil.copy2(source_specs / original, downloads / public)
    # 仅发布无凭据的外部联调材料；独立仓库从source/testing重建。
    testing = ROOT / 'testing'
    if not testing.exists():
        testing = ROOT.parent / 'docs' / 'testing'
    for name in ['121-open-api-external.postman_collection.json',
                 '121-open-api-test.postman_environment.json',
                 'external-testing.md', 'OpenApiExample.java']:
        shutil.copy2(testing / name, downloads / name)
    (downloads / 'api-reference.md').write_text(source, encoding='utf-8', newline='\n')
    print(json.dumps({'output': str(output), 'sections': section_num, 'headings': len(headings),
                      'jsonExamples': sum(1 for t in tokens if t.type == 'fence' and t.info == 'json')}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=ROOT / 'site')
    build(parser.parse_args().output.resolve())
