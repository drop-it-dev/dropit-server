"""Draw stage-to-stage lines from saved k6 summaries (not a fabricated time series)."""

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
RAW = ROOT / "results" / "raw"
OUTPUT = ROOT / "results" / "drop-list-cache" / "drop-list-all-stages-before-after.jpg"
FONT = "C:/Windows/Fonts/malgun.ttf"


def p95(name, metric="drop_list_opening_duration"):
    with (RAW / name).open(encoding="utf-8") as source:
        return json.load(source)["metrics"][metric]["values"]["p(95)"]


def improvement(before, after):
    return 100 * (before - after) / before


db_to_redis = [
    (p95("portfolio-redis-off-opening-50-sep15.json"),
     p95("portfolio-redis-on-opening-50-sep15.json")),
    (p95("portfolio-redis-off-opening-50-repeat-sep15.json"),
     p95("portfolio-redis-on-opening-50-repeat-sep15.json")),
]
redis_to_old_l1 = [
    (p95("opening-4000-redis-sep16.json"),
     p95("opening-4000-l1-sep16.json")),
    (p95("opening-4000-redis-repeat-sep16.json"),
     p95("opening-4000-l1-repeat-sep16.json")),
]
lock_before = p95("opening-4000-lock-before-sep16.json")
lock_after = [
    p95("opening-4000-lock-after-1-sep16.json"),
    p95("opening-4000-lock-after-2-sep16.json"),
]
million = [
    (p95("portfolio-million-redis-1000rps-sep15.json", "http_req_duration"),
     p95("portfolio-million-local-1000rps-sep15.json", "http_req_duration"))
]

canvas = Image.new("RGB", (2040, 1390), "#f4f7fb")
draw = ImageDraw.Draw(canvas)


def font(size):
    return ImageFont.truetype(FONT, size)


def label(x, y, value, size=22, color="#263b50"):
    draw.text((x, y), value, font=font(size), fill=color)


label(60, 29, "Drop 목록 조회 | 적용 단계별 Before / After", 42, "#152a40")
label(62, 89, "같은 API를 각 조건에서 반복 측정한 p95. 숫자가 낮을수록 사용자가 덜 기다립니다.", 24, "#607185")


def card(x, y, title, context, stages, rows, maximum, unit, note, color):
    width, height = 955, 540
    draw.rounded_rectangle((x, y, x + width, y + height), radius=22,
                           fill="#ffffff", outline="#dce5ef", width=2)
    label(x + 34, y + 24, title, 31)
    label(x + 36, y + 73, context, 21, "#607185")

    left, right = x + 230, x + 755
    top, bottom = y + 162, y + 315
    for tick in (0, 0.5, 1):
        tick_value = maximum * tick
        py = round(bottom - tick * (bottom - top))
        draw.line((left - 12, py, right + 12, py), fill="#e9eef5", width=2)
        label(x + 91, py - 12, f"{tick_value:.1f}", 17, "#8493a4")
    label(x + 42, top - 30, f"p95 ({unit})", 18, "#617185")
    for index, (before, after) in enumerate(rows):
        first = round(bottom - before / maximum * (bottom - top))
        second = round(bottom - after / maximum * (bottom - top))
        line_color = color if index == 0 else "#e19c42"
        draw.line((left, first, right, second), fill=line_color, width=7)
        for px, py in ((left, first), (right, second)):
            draw.ellipse((px - 9, py - 9, px + 9, py + 9), fill="#ffffff",
                         outline=line_color, width=5)
        label(x + 38, y + 380 + index * 37,
              f"{index + 1}차: {before:.2f} → {after:.2f} {unit}   "
              f"({abs(improvement(before, after)):.1f}% "
              f"{'감소' if after < before else '증가'})", 21)
    label(left - 86, bottom + 25, stages[0], 21)
    label(right - 130, bottom + 25, stages[1], 21)
    draw.rounded_rectangle((x + 30, y + 475, x + width - 30, y + 520),
                           radius=12, fill="#edf5fa")
    label(x + 50, y + 484, note, 19, "#275f83")


card(45, 145, "1. Redis 적용 전 → 적용 후", "50명 동시 단발 조회 · Spring 2대",
     ("DB 직접 조회", "Redis 캐싱"),
     [(a / 1000, b / 1000) for a, b in db_to_redis],
     25, "초", "두 차례 모두 Redis 적용 후 p95 감소", "#2d75a8")
card(1040, 145, "2. L1 초기 적용: 역효과", "4,000명 동시 단발 조회 · Redis는 이미 적용",
     ("Redis만 사용", "기존 L1"),
     [(a / 1000, b / 1000) for a, b in redis_to_old_l1],
     15, "초", "기존 L1은 4,000명 집중 요청에서 두 차례 모두 느려짐", "#b96161")
card(45, 715, "3. L1 잠금 방식 개선", "4,000명 · 기존 L1 한 번 vs 개선 L1 두 번",
     ("기존 L1", "잠금 개선 L1"),
     [(lock_before / 1000, after / 1000) for after in lock_after],
     13, "초", "10.92초 → 4.68초 / 7.86초: 각각 57.1% / 28.0% 감소", "#2b9a70")
card(1040, 715, "4. 100만 건 지속 조회", "1,000 RPS · 약 18분 · 별도 장시간 시나리오",
     ("Redis만 사용", "Redis + L1"),
     million, 12, "ms", "이 조건에서는 p95 10.85ms → 2.48ms (77.2% 감소)", "#2d75a8")

label(65, 1300,
      "읽는 법: 선이 내려가면 빨라집니다. 이 선은 각 테스트의 집계값을 연결한 것이며, 실제 시간별 요청 변화 그래프가 아닙니다.",
      21, "#607185")
label(65, 1335,
      "주의: 50명·4,000명·100만 건은 부하 조건이 달라 서로 직접 비교하지 않습니다. 4,000명 DB 직접 조회는 실행하지 않았습니다.",
      21, "#607185")

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
canvas.save(OUTPUT, quality=95, subsampling=0)
print(OUTPUT)
