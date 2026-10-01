from pathlib import Path
import re, sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "project")
app = root / "app/src/main/assets/app.js"
idx = root / "app/src/main/assets/index.html"
s = app.read_text(encoding="utf-8")

def between(text, start, end, repl):
    a = text.index(start)
    b = text.index(end, a)
    return text[:a] + repl + text[b:]

# Artistic but exact palette covers. Every color remains a separate real segment.
s = re.sub(
    r"function paintHTML\(p\)\{.*?\}\nfunction heart",
    r'''function paintHTML(p){const colors=p.colors.map(c=>c.hex),mode=(Number(p.number)+p.volume*7+colors.length*3)%4;return `<span class="paint-grid paint-mode-${mode}" data-paint-count="${colors.length}" style="background:${colors[0]}">${colors.map((hex,i)=>`<i class="paint" aria-hidden="true" data-index="${i}" style="background:${hex}"></i>`).join('')}</span>`}\nfunction heart''',
    s,
    count=1,
    flags=re.S,
)

# Compact home identity. It is intentionally not a tall separate hero/header.
s = re.sub(
    r"function mast\(\)\{.*?\}\nfunction bottom",
    r'''function mast(){return `<div class="home-identity"><h1>Цвета Сандзо Вада</h1><div class="identity-mark" aria-hidden="true"><i></i><i></i><b></b></div></div>`}\nfunction bottom''',
    s,
    count=1,
    flags=re.S,
)

# Bottom navigation is navigation again: Home / Favorites / Settings.
bottom = r'''function bottom(){return `<nav class="bottom" aria-label="Основная навигация">
<button class="navbtn ${state.tab==='catalog'?'active':''}" data-tab="catalog" ${state.tab==='catalog'?'aria-current="page"':''}><span class="navicon"><svg aria-hidden="true" viewBox="0 0 28 28" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M5 13.2 14 5l9 8.2v9.3a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 5 22.5Z"/><path d="M10.5 24v-7.5h7V24"/></svg></span>Главная</button>
<button class="navbtn ${state.tab==='favorites'?'active':''}" data-tab="favorites" ${state.tab==='favorites'?'aria-current="page"':''}><span class="navicon"><svg aria-hidden="true" viewBox="0 0 28 28" fill="currentColor"><path d="M14 24C10.7 20.9 4 17 4 11.4 4 7.9 6.5 5.5 9.5 5.5c2 0 3.6 1.1 4.5 2.6.9-1.5 2.6-2.6 4.5-2.6 3.1 0 5.5 2.4 5.5 5.9C24 17 17.3 20.9 14 24Z"/></svg></span>Избранное</button>
<button class="navbtn ${state.tab==='settings'?'active':''}" data-tab="settings" ${state.tab==='settings'?'aria-current="page"':''}><span class="navicon"><svg aria-hidden="true" viewBox="0 0 28 28" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="14" cy="14" r="4"/><path d="M14 3.5v3M14 21.5v3M3.5 14h3M21.5 14h3M6.6 6.6l2.1 2.1M19.3 19.3l2.1 2.1M21.4 6.6l-2.1 2.1M8.7 19.3l-2.1 2.1"/></svg></span>Настройки</button>
</nav>`}'''
s = between(s, "function bottom()", "function filtered", bottom + "\n")

# Small filter control directly under search. Active conditions remain visible but compact.
helper = r'''
function catalogFilterControl(){const summary=filterSummary();return `<div class="catalog-filter-control"><button class="filter-icon-btn ${filterChoiceCount()?'has-active':''}" id="filterButton" aria-label="Открыть фильтры каталога" aria-expanded="${state.filtersOpen}"><svg aria-hidden="true" viewBox="0 0 28 28" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M5 7h18M8 14h12M11 21h6"/><circle cx="10" cy="7" r="2" fill="currentColor" stroke="none"/><circle cx="17" cy="14" r="2" fill="currentColor" stroke="none"/><circle cx="14" cy="21" r="2" fill="currentColor" stroke="none"/></svg>${filterChoiceCount()?`<b>${filterChoiceCount()}</b>`:''}</button>${summary?`<div class="compact-filter-summary"><span>${esc(summary)}</span><button id="resetFiltersCompact">Сбросить</button></div>`:`<span class="catalog-state">Все сочетания</span>`}</div>`}
'''
needle = "const filterChoiceCount=()=>[state.volume,state.count,state.category].filter(Boolean).length;"
if needle not in s:
    raise RuntimeError("filterChoiceCount marker missing")
s = s.replace(needle, needle + helper, 1)

# Third filter group: no explanatory text; horizontal scroll behavior remains.
s = s.replace('<div class="sheet-label">Категория <span>прокручивается отдельно</span></div>', '<div class="sheet-label">Категории</div>')

# Catalog: compact title, search, then filter icon. No tall mast/header.
catalog = r'''function catalog(base=D){const list=filtered(base),shown=list.slice(0,state.limit);return `<main class="content page home-page">${mast()}<div class="search-wrap"><label class="sr-only" for="search">Поиск сочетаний</label><input id="search" class="search" inputmode="search" autocomplete="off" spellcheck="false" aria-label="Поиск по номеру, цвету, HEX или категории" placeholder="Номер, цвет, HEX или категория" value="${esc(state.query)}"><button class="clear-btn" id="clear" aria-label="Очистить поиск">×</button></div>${catalogFilterControl()}<div class="section-meta"><strong>${list.length} из ${base.length===D.length?581:base.length}</strong><span>${filterSummary()||'все сочетания'}</span></div>${list.length?`<div class="grid">${shown.map(card).join('')}</div>${shown.length<list.length?`<button class="load-more" id="more">Показать ещё · ${list.length-shown.length}</button>`:''}`:`<div class="empty"><div class="empty-art" aria-hidden="true"><i class="e1"></i><i class="e2"></i><i class="e3"></i></div><h2>Ничего не найдено</h2><p>Откройте фильтры или сбросьте активные условия.</p></div>`}</main>${bottom()}${state.filtersOpen?filterSheet():''}`}'''
s = between(s, "function catalog(base=D)", "const cats=", catalog + "\n")

# Favorites / settings: use compact page headings, not the home identity block.
s = re.sub(
    r"function favorites\(\)\{const list=D\.filter\(p=>fav\.has\(p\.id\)\);return `\$\{mast\(\)\}<main class=\"content page\">",
    "function favorites(){const list=D.filter(p=>fav.has(p.id));return `<main class=\"content page secondary-page\"><div class=\"secondary-head\"><h1>Избранное</h1></div>",
    s,
    count=1,
)
s = s.replace('function settings(){return `${mast()}<main class="content page settings-page"><h2 class="page-title">Настройки</h2>', 'function settings(){return `<main class="content page settings-page secondary-page"><div class="secondary-head"><h1>Настройки</h1></div>')

# Bind: filter button is inside Home, not bottom nav.
s = s.replace("$('#filtersNav')?.addEventListener('click',()=>{if(state.tab!=='catalog'||!state.filtersOpen)openFilters();else{state.filtersOpen=false;render(false)}});", "$('#filterButton')?.addEventListener('click',()=>openFilters());")

# 1.5.6 visible version.
s = re.sub(r"Версия 1\.5\.\d+\.", "Версия 1.5.6.", s)
app.write_text(s, encoding="utf-8")

html = idx.read_text(encoding="utf-8")
css = r'''
/* 1.5.6 — restored Wada identity, compact home, exact artistic covers */
:root{--bg:#11110f;--bg2:#171714;--panel:#1c1b18;--panel2:#24221e;--line:#37332d;--paper:#eee7d8;--muted:#979087;--nav:#18171b}
html,body{background-color:var(--bg)}
body{background:
 radial-gradient(ellipse at 104% 7%,rgba(28,127,143,.075),transparent 28%),
 radial-gradient(ellipse at -6% 38%,rgba(206,103,45,.055),transparent 27%),
 radial-gradient(ellipse at 82% 82%,rgba(130,118,155,.055),transparent 30%),
 linear-gradient(155deg,#11110f 0%,#12120f 49%,#10100e 100%)}
body:before{z-index:0;opacity:.14;background:
 radial-gradient(circle at 12% 18%,rgba(255,255,255,.08) 0 .7px,transparent .9px) 0 0/19px 21px,
 radial-gradient(circle at 72% 41%,rgba(255,255,255,.045) 0 .6px,transparent .9px) 0 0/13px 17px,
 repeating-linear-gradient(106deg,transparent 0 73px,rgba(222,211,190,.016) 74px 75px,transparent 76px 151px);mix-blend-mode:soft-light}
body:after{content:"";position:fixed;pointer-events:none;z-index:0;right:-92px;top:20vh;width:210px;height:210px;border:1px solid rgba(166,155,191,.055);border-radius:43% 57% 52% 48%/61% 39% 55% 45%;transform:rotate(18deg);box-shadow:-180px 360px 0 -56px rgba(213,155,72,.018)}
#app{position:relative;z-index:1}

/* Compact identity immediately above search */
.home-page{padding-top:calc(15px + env(safe-area-inset-top))}
.home-identity{position:relative;display:flex;align-items:center;justify-content:space-between;min-height:54px;margin:0 2px 9px;padding:0 1px}
.home-identity h1{position:relative;z-index:2;margin:0;font-size:31px;line-height:1;letter-spacing:-.035em;font-weight:735;color:var(--paper)}
.identity-mark{position:relative;width:82px;height:45px;flex:0 0 82px;opacity:.82}
.identity-mark i,.identity-mark b{position:absolute;display:block}
.identity-mark i:first-child{width:38px;height:35px;right:32px;top:4px;background:#a04e30;border-radius:59% 41% 52% 48%/45% 57% 43% 55%;transform:rotate(-13deg)}
.identity-mark i:nth-child(2){width:35px;height:39px;right:7px;top:2px;background:#1d6974;border-radius:41% 59% 46% 54%/61% 39% 57% 43%;transform:rotate(12deg)}
.identity-mark b{width:58px;height:1px;right:4px;top:25px;background:rgba(226,217,201,.34);transform:rotate(-27deg)}
.home-page .search-wrap{margin-bottom:9px}
.home-page .search{height:51px;background:rgba(20,19,17,.8);backdrop-filter:blur(8px);border-color:#49453f}

/* Filter trigger under search, not navigation */
.catalog-filter-control{display:flex;align-items:center;gap:9px;min-height:41px;margin:0 1px 12px}
.filter-icon-btn{position:relative;width:43px;height:39px;flex:0 0 43px;border:1px solid #49443e;background:linear-gradient(145deg,#1c1a17,#141311);color:#c9c0d2;border-radius:13px 17px 12px 16px;display:grid;place-items:center;box-shadow:inset 0 1px rgba(255,255,255,.025)}
.filter-icon-btn svg{width:23px;height:23px}.filter-icon-btn.has-active{border-color:#71667d;color:#e1d6ec;background:#211e25}.filter-icon-btn b{position:absolute;right:-5px;top:-5px;min-width:17px;height:17px;padding:0 4px;border-radius:10px;background:#c18a43;color:#17130e;font-size:9px;display:grid;place-items:center}
.catalog-state{font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:#777169}
.compact-filter-summary{display:flex;align-items:center;gap:7px;min-width:0;overflow:hidden}.compact-filter-summary>span{font-size:11px;color:#aaa39a;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.compact-filter-summary>button{border:0;background:transparent;color:#b9acc9;font-size:11px;padding:5px 3px;flex:0 0 auto}

/* Bottom nav is navigation only */
.bottom{grid-template-columns:repeat(3,1fr)}
.navbtn.active .navicon{background:#554e64}

/* Filter third group scrolls, without instructional copy */
.sheet-label span{display:none!important}

/* Artistic exact-color cover system: every i is a real palette color. */
.paint-grid{position:absolute!important;inset:0!important;display:flex!important;width:100%;height:100%;overflow:hidden;background:inherit}
.paint-grid .paint,.hero-palette .paint-grid .paint{position:relative!important;inset:auto!important;height:100%!important;min-width:0!important;opacity:1!important;transform:none!important;z-index:auto!important;display:block!important;margin:0!important}
.paint-grid .paint:nth-child(3n+1){flex:1.22 1 0!important}.paint-grid .paint:nth-child(3n+2){flex:.92 1 0!important}.paint-grid .paint:nth-child(3n){flex:1.08 1 0!important}
.paint-grid .paint+.paint{margin-left:-7px!important;box-shadow:-1px 0 rgba(255,255,255,.07)}
.paint-mode-0 .paint{clip-path:polygon(7px 0,100% 0,calc(100% - 9px) 100%,0 100%)!important}.paint-mode-0 .paint:nth-child(even){clip-path:polygon(0 0,calc(100% - 6px) 0,100% 100%,8px 100%)!important}
.paint-mode-1 .paint{clip-path:polygon(0 0,100% 5px,calc(100% - 8px) 100%,7px calc(100% - 4px))!important}.paint-mode-1 .paint:nth-child(2n){clip-path:polygon(8px 4px,100% 0,100% calc(100% - 5px),0 100%)!important}
.paint-mode-2 .paint{clip-path:polygon(7px 0,100% 0,100% calc(100% - 7px),0 100%)!important}.paint-mode-2 .paint:nth-child(3n+2){clip-path:polygon(0 0,calc(100% - 9px) 0,100% 100%,8px calc(100% - 3px))!important}
.paint-mode-3 .paint{clip-path:polygon(0 4px,100% 0,calc(100% - 5px) 100%,6px 100%)!important}.paint-mode-3 .paint:nth-child(odd){clip-path:polygon(7px 0,100% 3px,100% 100%,0 calc(100% - 6px))!important}
.palette-art:after,.hero-palette:after{content:"";position:absolute;inset:0;pointer-events:none;background:linear-gradient(122deg,rgba(255,255,255,.035),transparent 34%,rgba(0,0,0,.035));mix-blend-mode:soft-light}
.card:nth-child(3n+1) .palette-art .paint-grid{transform:translateX(-1px)}.card:nth-child(3n+2) .palette-art .paint-grid{transform:scaleX(1.015)}

/* Secondary screens no longer repeat the home branding */
.secondary-page{padding-top:calc(24px + env(safe-area-inset-top))}
.secondary-head{margin:0 2px 22px}.secondary-head h1{margin:0;font-size:31px;line-height:1;letter-spacing:-.03em}
.settings-page>.page-title{display:none}.settings-page>.page-lead{margin-top:-12px}

/* Light theme keeps the same expensive paper-like composition */
html[data-theme="light"] body{background:
 radial-gradient(ellipse at 104% 7%,rgba(28,127,143,.075),transparent 28%),
 radial-gradient(ellipse at -6% 38%,rgba(190,102,48,.06),transparent 27%),
 linear-gradient(155deg,#f2eee4,#eee8dd 55%,#f5f1e9)}
html[data-theme="light"] body:after{border-color:rgba(101,91,120,.09)}
html[data-theme="light"] .home-identity h1{color:#292722}
html[data-theme="light"] .home-page .search{background:rgba(250,247,239,.86);border-color:#c9c0b4}
html[data-theme="light"] .filter-icon-btn{background:linear-gradient(145deg,#faf7ef,#e7dfd2);border-color:#c9c0b4;color:#655b78}
html[data-theme="light"] .catalog-state,html[data-theme="light"] .compact-filter-summary>span{color:#756f66}

@media(max-width:380px){.home-identity h1{font-size:28px}.identity-mark{width:66px;flex-basis:66px}.secondary-head h1{font-size:29px}}
'''
html = html.replace("\n</style>", css + "\n</style>", 1)
idx.write_text(html, encoding="utf-8")

# Assertions for the intended navigation contract.
assert '>Главная</button>' in s
assert '>Избранное</button>' in s
assert '>Настройки</button>' in s
assert 'id="filterButton"' in s
assert 'прокручивается отдельно' not in s
assert '581 сочетание · два тома · полностью офлайн' not in s
assert 'Цвета Сандзо Вада' in s
assert 'data-tab="categories"' not in s
assert 'paint-mode-${mode}' in s
assert 'Версия 1.5.6.' in s
print('1.5.6 restored identity UX applied')