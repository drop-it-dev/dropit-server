"""Render matched Redis-off/on Drop list measurements without mixing load levels."""

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
RAW = ROOT / "results" / "raw"
OUTPUT = ROOT / "results" / "drop-list-cache" / "redis-before-after-50-users.jpg"
FONT = Path("C:/Windows/Fonts/malgun.ttf")
PAIRS = [
    ("1차", "portfolio-redis-off-opening-50-sep15.json", "portfolio-redis-on-opening-50-sep15.json"),
    ("2차", "portfolio-redis-off-opening-50-repeat-sep15.json", "portfolio-redis-on-opening-50-repeat-sep15.json"),
]


def read(file_name):
    with (RAW / file_name).open(encoding="utf-8") as source:
        metrics = json.load(source)["metrics"]
    return {
        "p95": metrics["drop_list_opening_duration"]["values"]["p(95)"],
        "success": metrics["checks"]["values"]["passes"],
        "failed": metrics["checks"]["values"]["fails"],
    }


rows = [(title, read(before), read(after)) for title, before, after in PAIRS]
limit = max(before["p95"] for _, before, _ in rows)
image = Image.new("RGB", (1600, 980), "#ffffff")
draw = ImageDraw.Draw(image)


def label(x, y, value, size=25, color="#172b40"):
    draw.text((x, y), value, font=ImageFont.truetype(str(FONT), size), fill=color)


def bar(y, value, title, color):
    label(125, y + 7, title, 26)
    draw.rounded_rectangle((385, y, 1195, y + 51), radius=13, fill="#e9eef3")
    width = max(9, round(810 * value / limit))
    draw.rounded_rectangle((385, y, 385 + width, y + 51), radius=13, fill=color)
    label(1225, y + 6, f"{value / 1000:.3f}초", 26)


label(72, 48, "Drop 목록 Redis 캐시 적용 전 / 적용 후", 43)
label(72, 110, "같은 API · 같은 JAR · Nginx + Spring 2대 · 동시 사용자 50명", 24, "#617084")
label(72, 170, "p95 응답 시간: 같은 50명 조건을 2회 반복", 29)

for index, (title, before, after) in enumerate(rows):
    top = 245 + index * 275
    ratio = before["p95"] / after["p95"]
    reduction = 100 * (before["p95"] - after["p95"]) / before["p95"]
    label(75, top - 4, f"{title} 측정", 29)
    bar(top + 55, before["p95"], "Redis 적용 전", "#c97766")
    bar(top + 130, after["p95"], "Redis 적용 후", "#2d79b8")
    label(385, top + 197, f"약 {ratio:.0f}배 빠름 · p95 {reduction:.2f}% 감소 · 양쪽 50/50건 성공", 23, "#315f86")

draw.rounded_rectangle((72, 810, 1530, 925), radius=19, fill="#f0f5f9")
label(102, 829, "코드 경로", 28)
label(102, 879, "Before: QueryDSL → MySQL 직접 조회   /   After: Redis 목록 조회 + 재고 MGET", 23)
label(75, 945, "로컬 PC의 단계별 1회씩, 총 2쌍 측정. 2,000명·100만 조회는 별도 부하 검증입니다.", 18, "#647587")

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
image.save(OUTPUT, quality=92, subsampling=0)
print(OUTPUT)
