"""저장된 k6 원본으로 PR 문서, 비교 이미지, HTML, ZIP을 생성한다."""
from pathlib import Path
import json, html, base64, zipfile, shutil
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parent
out = root / 'results' / 'drop-million-report'
out.mkdir(exist_ok=True)
phases = [('before','Before'), ('after','인덱스'), ('optimized','인덱스 + 쿼리 개선')]
data = [json.loads((root/'results'/'raw'/f'drop-million-{p}.json').read_text(encoding='utf-8-sig'))['metrics'] for p,_ in phases]
def val(d,metric,key): return d.get(metric,{}).get('values',{}).get(key,0)
rows = []
for label,metric,key,unit in [('완료 HTTP 요청','http_reqs','count','건'),('평균 응답 시간','http_req_duration','avg','ms'),('p95 응답 시간','http_req_duration','p(95)','ms'),('HTTP 실패율','http_req_failed','rate','%'),('시작하지 못한 요청','dropped_iterations','count','건')]:
    values=[val(d,metric,key)*(100 if unit=='%' else 1) for d in data]
    rows.append([label]+[f'{v:,.2f} {unit}' if unit!='건' else f'{v:,.0f}건' for v in values])
table='| 지표 | Before | 인덱스 | 인덱스 + 쿼리 개선 |\n|---|---:|---:|---:|\n'+'\n'.join('| '+' | '.join(r)+' |' for r in rows)
image=Image.new('RGB',(1600,750),'#f4f7fb'); draw=ImageDraw.Draw(image)
font=lambda size,bold=False: ImageFont.truetype('C:/Windows/Fonts/malgunbd.ttf' if bold else 'C:/Windows/Fonts/malgun.ttf',size)
draw.text((55,35),'Drop 100만 건 · 조회 성능 개선',font=font(43,True),fill='#172b4d')
draw.text((55,100),'상품 10만 건 | 공개 판매 중 50만 건 | 동일 k6 부하 · 단계별 1회',font=font(24),fill='#526174')
xs=[55,580,900,1190]; widths=[510,310,280,350]
for j,t in enumerate(['지표','Before','인덱스','인덱스 + 쿼리 개선']): draw.text((xs[j],170),t,font=font(25,True),fill='#16385b')
for i,row in enumerate(rows):
    y=225+i*73
    draw.rounded_rectangle((40,y-8,1560,y+53),radius=8,fill='white')
    for j,t in enumerate(row): draw.text((xs[j],y),t,font=font(24,j==0),fill='#193c32' if j==3 else '#26364b')
draw.text((55,640),'변경: ID 20개 선조회 → 상세 조인 / 검색어 없는 count 조인 제거',font=font(24,True),fill='#172b4d')
draw.text((55,686),'단일 로컬 실험 · 실패/시간 초과 포함 · 운영 수용량이나 반복 검증 결과가 아님',font=font(21),fill='#536477')
image.save(out/'comparison.png')
md=f'''# Drop 100만 건 조회 성능 개선

## 🛠️ 주요 변경 사항

- 판매 중 Drop 첫 페이지를 마감 임박순으로 조회할 때 대량 조인·정렬이 발생하는 문제를 개선했습니다.
- 복합 인덱스를 적용한 뒤 효과를 확인하고, ID 선조회 및 조건부 count 조인 제거를 추가했습니다.
- 기존 Page 응답, 전체 개수, 검색·정렬 계약을 유지합니다.

## ✨ Before / After

{table}

![성능 비교](comparison.png)

HTTP 완료 수에는 오류 응답·시간 초과도 포함됩니다. 시작하지 못한 요청은 k6의 dropped_iterations이며 서버가 거절한 요청 수와 다릅니다. p95는 실패 요청을 포함하며 Before의 약 60초는 클라이언트 시간 초과의 영향을 받습니다.

## 실험 조건

- API: `GET /drops?status=OPEN&sortType=CLOSING_SOON&page=0&size=20`
- 상품 100,000건, Drop 1,000,000건, 공개 판매 중 500,000건. 판매자는 한 명인 집중 시나리오이며 실제 운영 규모라는 가정은 아닙니다.
- Docker MySQL 8.4, Spring 서버, Docker k6·InfluxDB·Grafana를 같은 Windows PC에서 실행했습니다.
- 75초 동안 10→20→50→100→200→0 RPS, 최대 500 VU, 종료 대기 30초. 단계별 1회 측정했습니다.
- 모든 단계에서 서버를 같은 설정으로 실행하고 API 사전 호출 후 측정했습니다. DB 버퍼 캐시는 초기화하지 않았습니다.
- 요청량·데이터·페이지 크기·인증·연결 풀 설정은 동일합니다. 인덱스 단계에서는 통계를 갱신했습니다.

## 원인과 변경 코드

기존에는 상품·판매자를 조인한 결과에서 조건에 맞는 50만 건을 정렬했습니다. 인덱스를 추가해도 진단 실행 계획은 기존 외래키 조회를 선택했습니다.

```java
// Before: 조인한 전체 후보를 정렬하고 페이지 조회
selectFrom(drop).join(drop.product, product).fetchJoin()
    .join(product.seller, user).fetchJoin()
    .where(conditions).orderBy(order).offset(offset).limit(size);

// After: 필요한 ID만 선조회하고 그 ID에 한해 상세 조인
select(drop.id).from(drop).where(conditions)
    .orderBy(order).offset(offset).limit(size);
selectFrom(drop).join(drop.product, product).fetchJoin()
    .join(product.seller, user).fetchJoin()
    .where(drop.id.in(ids)).orderBy(order);
```

위 코드는 흐름을 설명하는 발췌이며 실제 구현은 `DropRepositoryCustomImpl`입니다. 키워드가 있으면 ID·count 쿼리에도 상품·판매자 조인을 유지합니다. 키워드가 없으면 필수 FK 관계를 전제로 count 조인을 생략합니다. 상세 조회에서 정렬을 다시 지정해 IN 조건으로 순서가 바뀌지 않게 했습니다.

인덱스: `(visible, close_at, id DESC, open_at, remaining_quantity, product_id)`.

## SQL 진단

| 단계 | 목록 또는 ID 쿼리 | count |
|---|---:|---:|
| Before | 2,683ms | 1,916ms |
| 인덱스 | 3,293ms | 2,362ms |
| 쿼리 개선 | ID 20개 3.26ms | 714ms |

진단용 SQL은 Drop 컬럼만 선택하므로 실제 API의 fetch join 전체 투영과 같지 않습니다. 최종 ID 쿼리 시간에는 후속 상세 쿼리가 포함되지 않습니다. 이 값들을 API 개선율로 환산하지 않았으며 API 비교는 위 k6 표를 사용합니다.

## ⚠️ 주의 사항 및 남은 한계

- 정확한 totalElements를 유지하므로 최종 count도 인덱스 후보 약 80만 건을 읽습니다. 인덱스만으로 목표 부하를 모두 처리한다고 주장하지 않습니다.
- 단일 실행이라 반복 편차·통계적 유의성을 확인하지 않았습니다. 키워드 검색, 뒷페이지, 여러 판매자 분포의 성능은 이번 측정 범위 밖입니다.
- ID 선조회로 쿼리는 2회에서 3회로 증가합니다. 기존 read-only 트랜잭션 및 MySQL REPEATABLE READ 안에서 페이지와 상세를 읽습니다.
- 재고 컬럼을 포함하는 인덱스는 재고 변경 시 유지 비용이 있습니다. 재고 쓰기 부하 영향은 별도 검증이 필요합니다.
- Before/인덱스 단계의 시간 초과에서 기존 k6 JSON 검증이 null body 예외를 냈습니다. HTTP 지표는 저장됐으며 checks를 정확성 근거로 사용하지 않았습니다.
- 다음 후보: 정확한 전체 개수가 불필요한 경우 별도 Slice API, 검색어별 실행 계획 개선. API 계약 변경은 팀 합의 후 진행합니다.

## 🧪 테스트 결과

- [x] Drop 테스트 43개 통과, 실패 0개, 오류 0개
- [x] MySQL 통합 테스트 2개 포함: 키워드 검색 페이징·빈 페이지·전체 개수와 기본 조회의 기존 조인 쿼리 결과 일치
- [x] k6 세 단계 원본 결과 저장
- [ ] 최종 p95 2초 목표 미달

## 💡 관련 이슈

- 이슈 번호 미확인. 실제 번호를 확인한 뒤 연결합니다.

## GitHub에 이미지 올리기

PR 편집기에 `comparison.png`와 `grafana.png`를 드래그하면 GitHub 이미지 주소가 생성됩니다. 본문 이미지 경로를 해당 주소로 바꾸면 됩니다. 로컬 C: 경로는 GitHub에서 표시되지 않습니다.
'''
if (out/'grafana.png').exists(): md += '\n![Grafana 측정 화면](grafana.png)\n'
(out/'PR-review.md').write_text(md,encoding='utf-8')
for p,_ in phases:
    shutil.copy2(root/'results'/'raw'/f'drop-million-{p}.json',out/f'drop-million-{p}.json')
    shutil.copy2(root/'results'/'raw'/f'drop-million-{p}-explain.txt',out/f'drop-million-{p}-explain.txt')
for name in ['drop-index-seed.sql','drop-index-before-explain.sql','drop-index-after-explain.sql','drop-index-optimized-explain.sql']:
    shutil.copy2(root/'data'/name,out/name)
shutil.copy2(root/'k6'/'drop-list-index.js',out/'drop-list-index.js')
# HTML은 외부 라이브러리 없이 표와 문서를 한 파일로 보관한다.
png=base64.b64encode((out/'comparison.png').read_bytes()).decode()
body='<html lang="ko"><meta charset="utf-8"><title>Drop 성능 비교</title><style>body{max-width:1100px;margin:40px auto;padding:20px;font-family:Malgun Gothic,sans-serif;line-height:1.7;color:#24344b}img{width:100%}table{border-collapse:collapse;width:100%}th,td{padding:14px;border-bottom:1px solid #ddd;text-align:right}th:first-child,td:first-child{text-align:left}pre{white-space:pre-wrap;background:#f5f7fb;padding:24px;border-radius:12px}</style>'
body+=f'<h1>Drop 100만 건 조회 성능 개선</h1><p>상품 10만 건 · 공개 판매 중 Drop 50만 건 · 단계별 1회 측정</p><img src="data:image/png;base64,{png}">'
body+='<h2>Before / After</h2><table><tr><th>지표</th><th>Before</th><th>인덱스</th><th>쿼리 개선</th></tr>'
body+=''.join('<tr>'+''.join('<td>'+html.escape(c)+'</td>' for c in r)+'</tr>' for r in rows)+'</table>'
body+='<h2>무엇을 바꿨나?</h2><p>인덱스만 추가했을 때는 기존 조인 순서와 대량 정렬이 남았습니다. 필요한 ID 20개를 먼저 고르고 상세 정보를 조인하도록 바꾸고, 검색어가 없는 전체 개수 조회에서 상품·판매자 조인을 제거했습니다.</p><h2>검증과 한계</h2><p>Drop 테스트 43개 통과. 기존 Page 계약을 유지합니다. 최종 HTTP 실패율은 0%지만 p95는 23.46초로 목표 2초에 미달했습니다. 정확한 전체 개수 계산 비용이 남습니다. 단일 로컬 측정이며 시간 초과를 포함하므로 운영 수용량으로 해석할 수 없습니다.</p><p>요청 조건: /drops?status=OPEN&amp;sortType=CLOSING_SOON&amp;page=0&amp;size=20<br>75초 동안 10→200→0 RPS, 최대 500 VU. HTTP 요청 수에는 실패가 포함되며, 미시작 요청은 dropped_iterations입니다.</p>'
if (out/'grafana.png').exists(): body+='<img src="data:image/png;base64,'+base64.b64encode((out/'grafana.png').read_bytes()).decode()+'">'
(out/'report.html').write_text(body,encoding='utf-8')
with zipfile.ZipFile(root/'results'/'drop-million-report.zip','w',zipfile.ZIP_DEFLATED) as z:
    for f in out.iterdir(): z.write(f,f.name)
print(out)
