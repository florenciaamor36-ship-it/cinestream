#!/usr/bin/env python3
"""Build bundled Archive catalog from the official cursor-based scraping API."""
import gzip, html, json, pathlib, re, sys, time, urllib.parse, urllib.request
ROOT = pathlib.Path(__file__).resolve().parents[1]
ENDPOINT = 'https://archive.org/services/search/v1/scrape'
QUERY = 'mediatype:movies AND collection:feature_films'
EXPECTED = 29634
FIELDS = ['identifier','title','description','year','creator','language','licenseurl','subject','runtime','format']
COUNT = 5000
RESTRICTED = re.compile(r'\b(porn(?:ography|ographic)?|sexploitation|hardcore porn|softcore porn|adult (?:porn|sex) (?:film|video)|xxx porn)\b', re.I)
CHECKPOINT = ROOT / 'scratch/ia_scrape_checkpoint.json'
JSONL = ROOT / 'scratch/ia_scrape_items.jsonl'

def vals(value, limit=12, max_len=200):
    if value is None: return []
    if not isinstance(value, list): value = [value]
    out=[]
    for raw in value:
        t=re.sub(r'\s+',' ',html.unescape(str(raw))).strip()
        if t and t.casefold() not in {'none','null'}: out.append(t[:max_len])
        if len(out)>=limit: break
    return out

def yr(v):
    try:
        n=int(str(v).strip()[:4]) if v else None
        return n if n and 1800<=n<=2100 else None
    except (ValueError,TypeError): return None

def mins(v):
    if not v: return None
    s=str(v).strip()
    try:
        if ':' in s:
            a=[int(x) for x in s.split(':')]
            if len(a)==2: return max(1,a[0]+(a[1]>=30))
            if len(a)==3: return max(1,a[0]*60+a[1]+(a[2]>=30))
        m=re.match(r'[0-9.]+',s)
        if not m:return None
        n=float(m.group())
        return max(1,round(n/60)) if 'sec' in s.lower() else max(1,round(n))
    except Exception:return None

def clean_record(d):
    ident=str(d.get('identifier') or '').strip()
    if not ident:return None
    title=(vals(d.get('title') or ident,1,240) or [ident])[0]
    description=' '.join(vals(d.get('description'),12,1500))
    description=re.sub(r'\s+',' ',description).strip()[:3000]
    subjects=vals(d.get('subject'),20,160); formats=vals(d.get('format'),24,120)
    if RESTRICTED.search(' '.join([title,description,*subjects,*formats])): return None
    license_url=str(d.get('licenseurl') or '').strip()
    return {
      'identifier':ident,'title':title,'description':description,'year':yr(d.get('year')),
      'creator':vals(d.get('creator'),8,200),'language':vals(d.get('language'),10,100),
      'licenseUrl':license_url,
      'rightsStatus':'DECLARED_LICENSE_REVIEW_REQUIRED' if license_url else 'NO_LICENSE_DECLARATION_FOUND',
      'subjects':subjects,'formats':formats,'runtimeMinutes':mins(d.get('runtime')),
      'sourceUrl':'https://archive.org/details/'+urllib.parse.quote(ident,safe=''),
      'posterUrl':'https://archive.org/services/img/'+urllib.parse.quote(ident,safe='')
    }

def fetch(cursor):
    params=[('q',QUERY),('fields',','.join(FIELDS)),('count',str(COUNT))]
    if cursor:params.append(('cursor',cursor))
    req=urllib.request.Request(ENDPOINT+'?'+urllib.parse.urlencode(params),headers={'User-Agent':'CineStream catalog importer/0.4'})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req,timeout=90) as r:return json.load(r)
        except Exception as e:
            if attempt==4:raise
            time.sleep(2+attempt*2)

ROOT.joinpath('scratch').mkdir(exist_ok=True)
if CHECKPOINT.exists():
    cp=json.loads(CHECKPOINT.read_text()); cursor=cp.get('cursor'); source_total=cp.get('sourceTotal'); page=cp.get('page',0)
else:
    cursor=None; source_total=None; page=0
if JSONL.exists():
    records={}
    for line in JSONL.read_text().splitlines():
        if line.strip():
            obj=json.loads(line); records[obj['identifier']]=obj
else: records={}
mode='a' if JSONL.exists() else 'w'
with JSONL.open(mode,encoding='utf-8') as spool:
    while True:
        data=fetch(cursor); page+=1
        if source_total is None: source_total=int(data.get('total',0))
        for raw in data.get('items',[]):
            item=clean_record(raw)
            if item:
                records[item['identifier']]=item
                spool.write(json.dumps(item, ensure_ascii=False, separators=(',', ':'))+'\n')
        spool.flush()
        cursor=data.get('cursor')
        CHECKPOINT.write_text(json.dumps({'cursor':cursor,'sourceTotal':source_total,'page':page,'kept':len(records)}))
        print(f'Page {page}: kept {len(records)} / source total {source_total}',file=sys.stderr,flush=True)
        if not cursor:break
        time.sleep(.2)

if source_total!=EXPECTED: raise SystemExit(f'Expected {EXPECTED}, got {source_total}; do not replace catalog')
items=sorted(records.values(),key=lambda x:(-(x['year'] or 0),x['title'].casefold(),x['identifier']))
out={'source':'Internet Archive Advanced Search scraping API','query':QUERY,'sourceRecordCount':source_total,'catalogCandidateCount':len(items),
'rightsNotice':'Internet Archive presence or a declared license does not guarantee legal reuse. Check the item rights statement. CineStream starts playback only for records currently declaring Creative Commons Public Domain Mark 1.0; all other entries link to their original Archive page.',
'audioNotice':'Metadata language is not proof of audio or subtitle language. CineStream does not dub, translate, or replace media tracks.',
'metadataLanguageNotice':'Titles and descriptions in English or Portuguese can be translated into Spanish on the device using downloadable ML Kit language models. Original text remains available; machine translation can be imperfect.',
'items':items}
path=ROOT/'app/src/main/assets/ia_catalog_v2.dat'
path.parent.mkdir(parents=True,exist_ok=True)
with gzip.open(path,'wt',encoding='utf-8',compresslevel=9) as stream:
    json.dump(out,stream,ensure_ascii=False,separators=(',',':'))
print(f'Wrote {len(items)} records from {source_total}; {path.stat().st_size} compressed bytes')
