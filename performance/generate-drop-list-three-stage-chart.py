"""Show measured Redis-off, Redis-only and Redis+L1 opening stages."""

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
RAW = ROOT / "results" / "raw"
OUTPUT = ROOT / "results" / "drop-list-cache" / "three-stage-50-users.jpg"
FONT = Path("C:/Windows/Fonts/malgun.ttf")
RUNS = [
    ("1차", (
        "portfolio-redis-off-opening-50-sep15.json",
        "portfolio-redis-on-opening-50-sep15.json",
        "portfolio-redis-l1-opening-50-sep15.json",
    )),
    ("2차", (
        "portfolio-redis-off-opening-50-repeat-sep15.json",
        "portfolio-redis-on-opening-50-repeat-sep15.json",
        "portfolio-redis-l1-opening-50-repeat-sep15.json",
    )),
]


def p95(file_name):
    with (RAW / file_name).open(encoding="utf-8") as source:
        metrics = json.load(source)["metrics"]
    assert metrics["checks"]["values"]["passes"] == 50
    assert metrics["checks"]["values"]["fails"] == 0
    return metrics["drop_list_opening_duration"]["values"]["p(95)"]


measured = [(title, [p95(name) for name in names]) for title, names in RUNS]
limit = max(values[0] for _, values in measured)
image = Image.new("RGB", (1600, 1190), "#ffffff")
draw = ImageDraw.Draw(image)


def label(x, y, value, size=24, color="#1c3044"):
    draw.text((x, y), value, font=ImageFont.truetype(str(FONT), size), fill=color)


def bar(y, name, value, color):
    label(130, y + 5, name, 25)
    draw.rounded_rectangle((440, y, 1260, y + 45), radius=12, fill="#e8eef3")
    draw.rounded_rectangle((440, y, 440 + max(9, round(820 * value / limit)), y + 45), radius=12, fill=color)
    display = f"{value / 1000:.3f}초" if value >= 1000 else f"{value:.2f}ms"
    label(1290, y + 4, display, 26)


label(75, 48, "Drop 목록 성능 3단계: Redis 전 → Redis 후 → L1 추가", 40)
label(75, 111, "동일 API · 동일 JAR · Nginx + Spring 2대 · 동시 사용자 50명 · 각 단계 2회", 23, "#65758a")

for index, (title, values) in enumerate(measured):
    top = 205 + index * 405
    db, redis, l1 = values
    redis_drop = 100 * (db - redis) / db
    l1_change = 100 * (l1 - redis) / redis
    total_drop = 100 * (db - l1) / db
    label(78, top - 5, f"{title} 측정: 모두 50/50건 정상 응답", 28)
    bar(top + 62, "Redis 캐싱 전", db, "#cc7668")
    bar(top + 122, "Redis 캐싱 후", redis, "#337db8")
    bar(top + 182, "서버별 L1 추가", l1, "#43a489")
    label(440, top + 250, f"Redis 적용: p95 {redis_drop:.2f}% 감소", 24, "#2c729f")
    l1_text = (f"L1 추가: p95 {l1_change:.2f}% 증가 (악화)" if l1_change > 0
               else f"L1 추가: p95 {-l1_change:.2f}% 감소")
    label(440, top + 290, l1_text, 24, "#bd704b" if l1_change > 0 else "#38846c")
    label(440, top + 330, f"최초 DB 직접 조회 대비 최종 p95 {total_drop:.2f}% 감소", 23, "#315f86")

draw.rounded_rectangle((75, 1020, 1525, 1130), radius=18, fill="#f1f6fa")
label(100, 1040, "50명에서 L1 효과는 일정하지 않음", 27)
label(100, 1085, "100만 조회 지속 테스트의 L1 효과: 정상 응답 +57,632건 · HTTP 실패 38,154→0건 · p95 77.15% 감소", 19)
label(77, 1152, "Redis 전 100만 요청은 DB 포화 위험으로 실행하지 않았습니다. 고부하 비교는 별도 그래프를 보세요.", 18, "#65758a")

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
image.save(OUTPUT, quality=92, subsampling=0)
print(OUTPUT)
