import json, time, urllib.parse, urllib.request, re, sys
BASE='https://archive.org/advancedsearch.php'
LICENSE='http://creativecommons.org/publicdomain/mark/1.0/'
QUERY='mediatype:movies AND collection:feature_films AND licenseurl:"http://creativecommons.org/publicdomain/mark/1.0/"'
FIELDS=['identifier','title','description','year','date','creator','language','licenseurl','subject','runtime']
rows=100
found=None
items=[]
for start in range(0, 3000, rows):
 params=[('q',QUERY),('rows',str(rows)),('page',str(start//rows+1)),('output','json'),('sort[]','title asc')]+[('fl[]',x) for x in FIELDS]
 url=BASE+'?'+urllib.parse.urlencode(params)
 req=urllib.request.Request(url,headers={'User-Agent':'CineStream catalog importer/0.1 (personal project)'})
 for attempt in range(4):
  try:
   with urllib.request.urlopen(req,timeout=45) as r: data=json.load(r)
   break
  except Exception:
   if attempt==3: raise
   time.sleep(1.5*(attempt+1))
 resp=data.get('response',{})
 found=resp.get('numFound',0)
 docs=resp.get('docs',[])
 if not docs: break
 for d in docs:
  identifier=d.get('identifier')
  if not identifier or d.get('licenseurl') != LICENSE: continue
  title=re.sub(r'\s+',' ',str(d.get('title') or identifier)).strip()
  desc=d.get('description') or ''
  if isinstance(desc,list): desc=' '.join(map(str,desc))
  desc=re.sub(r'<[^>]+>',' ',str(desc))
  desc=re.sub(r'\s+',' ',desc).strip()[:1600]
  subject=d.get('subject') or []
  if isinstance(subject,str): subject=[subject]
  if not isinstance(subject,list): subject=[str(subject)]
  year=d.get('year')
  try: year=int(str(year)[:4]) if year else None
  except (ValueError,TypeError): year=None
  language=d.get('language') or []
  if isinstance(language,str): language=[language]
  if not isinstance(language,list): language=[str(language)]
  creator=d.get('creator') or []
  if isinstance(creator,str): creator=[creator]
  if not isinstance(creator,list): creator=[str(creator)]
  runtime=d.get('runtime')
  try: runtime=float(str(runtime).split()[0]) if runtime else None
  except (ValueError,TypeError): runtime=None
  items.append({
   'identifier':identifier,'title':title,'description':desc,
   'year':year,'creator':creator[:5],'language':language[:5],
   'subjects':[re.sub(r'\s+',' ',str(x)).strip()[:100] for x in subject[:12]],
   'runtime':runtime,
   'licenseUrl':LICENSE,
   'rightsStatus':'DECLARED_PUBLIC_DOMAIN_MARK_REVIEW_REQUIRED',
   'sourceUrl':f'https://archive.org/details/{urllib.parse.quote(identifier,safe="")}',
   'posterUrl':f'https://archive.org/services/img/{urllib.parse.quote(identifier,safe="")}'
  })
 print(f'Fetched {start+len(docs)}/{found}, retained {len(items)}',file=sys.stderr)
 if start+len(docs)>=found: break
 time.sleep(0.2)
# Deduplicate by identifier preserving API order
unique={x['identifier']:x for x in items}
items=list(unique.values())
if found != 2300 or len(items) != 2300:
 raise SystemExit(f'Expected 2300 results, found={found} retained={len(items)}')
out={'source':'Internet Archive Advanced Search','query':QUERY,'declaredLicense':LICENSE,'candidateCount':len(items),'rightsNotice':'Internet Archive metadata claims this license; this is not legal verification. Review the rights record for each title before redistributing or promoting it.','items':items}
out_path = __import__('pathlib').Path(__file__).resolve().parents[1] / 'app/src/main/assets/ia_public_domain_candidates.json'
out_path.parent.mkdir(parents=True, exist_ok=True)
with out_path.open('w', encoding='utf-8') as f: json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
print(f'Wrote {len(items)} entries to {out_path} ({out_path.stat().st_size} bytes)')
