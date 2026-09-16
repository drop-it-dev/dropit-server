"""Render discrete, measured Before/After slope charts; never fabricate a time series."""

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
RAW = ROOT / "results" / "raw"
OUTPUT = ROOT / "results" / "drop-list-cache" / "grafana-measured-slope-comparison.jpg"
FONT = "C:/Windows/Fonts/malgun.ttf"


def p95(file_name, metric):
    with (RAW / file_name).open(encoding="utf-8") as source:
        summary = json.load(source)
    return summary["metrics"][metric]["values"]["p(95)"]


def reduction(before, after):
    return 100 * (1 - after / before)


opening_50 = [
    (
        p95("portfolio-redis-off-opening-50-sep15.json", "drop_list_opening_duration"),
        p95("portfolio-redis-on-opening-50-sep15.json", "drop_list_opening_duration"),
    ),
    (
        p95("portfolio-redis-off-opening-50-repeat-sep15.json", "drop_list_opening_duration"),
        p95("portfolio-redis-on-opening-50-repeat-sep15.json", "drop_list_opening_duration"),
    ),
]
opening_4000 = [
    (
        p95("opening-4000-redis-sep16.json", "drop_list_opening_duration"),
        p95("opening-4000-l1-sep16.json", "drop_list_opening_duration"),
    ),
    (
        p95("opening-4000-redis-repeat-sep16.json", "drop_list_opening_duration"),
        p95("opening-4000-l1-repeat-sep16.json", "drop_list_opening_duration"),
    ),
]
million = [
    (
        p95("portfolio-million-redis-1000rps-sep15.json", "http_req_duration"),
        p95("portfolio-million-local-1000rps-sep15.json", "http_req_duration"),
    )
]

canvas = Image.new("RGB", (1950, 930), "#f4f7fb")
draw = ImageDraw.Draw(canvas)


def font(size):
    return ImageFont.truetype(FONT, size)


def text(x, y, value, size=21, color="#22354b"):
    draw.text((x, y), value, font=font(size), fill=color)


text(45, 30, "Drop 목록 조회 | Grafana 실측값으로 본 적용 전·후", 42, "#172b42")
text(47, 91, "연결선은 두 단계의 집계 결과를 비교합니다. 실제 요청 시간별 추이나 예상값이 아닙니다.", 22, "#617084")


def card(x, title, subtitle, rows, scale, unit, left_stage, right_stage, formatter, conclusion):
    left, top, width, height = x, 154, 605, 708
    draw.rounded_rectangle((left, top, left + width, top + height), radius=22, fill="#ffffff", outline="#dce5ef", width=2)
    text(left + 29, top + 25, title, 31)
    text(left + 30, top + 75, subtitle, 19, "#607185")

    plot_left, plot_right = left + 115, left + 490
    plot_top, plot_bottom = top + 180, top + 415
    ticks = {25: (0, 5, 10, 15, 20, 25), 15: (0, 5, 10, 15), 12: (0, 4, 8, 12)}[scale]
    for tick in ticks:
        y = round(plot_bottom - tick / scale * (plot_bottom - plot_top))
        draw.line((plot_left - 35, y, plot_right + 22, y), fill="#e9eef5", width=2)
        text(plot_left - 93, y - 11, f"{tick:.0f}", 15, "#8996a5")
    text(plot_left - 88, plot_top - 33, f"p95 ({unit})", 16, "#607185")

    colors = ["#2a78af", "#e0a34e"]
    for index, (before, after) in enumerate(rows):
        y0 = round(plot_bottom - before / scale * (plot_bottom - plot_top))
        y1 = round(plot_bottom - after / scale * (plot_bottom - plot_top))
        color = colors[index]
        draw.line((plot_left, y0, plot_right, y1), fill=color, width=6)
        for px, py in ((plot_left, y0), (plot_right, y1)):
            draw.ellipse((px - 9, py - 9, px + 9, py + 9), fill="#ffffff", outline=color, width=5)

    text(plot_left - 50, plot_bottom + 25, left_stage, 20, "#34495e")
    text(plot_right - 75, plot_bottom + 25, right_stage, 20, "#34495e")
    for index, (before, after) in enumerate(rows):
        color = colors[index]
        draw.ellipse((left + 35, top + 522 + index * 46, left + 49, top + 536 + index * 46), fill=color)
        text(left + 62, top + 510 + index * 46, f"{index + 1}차  {formatter(before)} → {formatter(after)}", 22)
    draw.rounded_rectangle((left + 28, top + 620, left + width - 28, top + 677), radius=14, fill="#eff5fa")
    text(left + 48, top + 633, conclusion(rows), 23, "#23618d" if reduction(*rows[0]) > 0 else "#9c5b35")


card(
    35,
    "동시 사용자 50명",
    "Redis 적용 전 DB → Redis 적용 후",
    [(a / 1000, b / 1000) for a, b in opening_50],
    25,
    "초",
    "DB 직접 조회",
    "Redis 캐싱",
    lambda value: f"{value:.2f}초" if value >= 1 else f"{value * 1000:.2f}ms",
    lambda rows: f"Redis 후 p95  {reduction(*rows[0]):.2f}% / {reduction(*rows[1]):.2f}% 감소",
)
card(
    673,
    "동시 사용자 4,000명",
    "Redis 적용 후 → 서버별 L1 추가",
    [(a / 1000, b / 1000) for a, b in opening_4000],
    15,
    "초",
    "Redis만 사용",
    "Redis + L1",
    lambda value: f"{value:.2f}초",
    lambda rows: f"L1 후 p95  {-reduction(*rows[0]):.2f}% / {-reduction(*rows[1]):.2f}% 악화",
)
card(
    1311,
    "100만 건 지속 조회",
    "1,000 RPS · 18분, Redis → L1 추가",
    million,
    12,
    "ms",
    "Redis만 사용",
    "Redis + L1",
    lambda value: f"{value:.2f}ms",
    lambda rows: f"L1 후 p95  {reduction(*rows[0]):.2f}% 감소",
)

text(50, 884, "주의: 4,000명 DB 직접 조회는 미실행. 50명·4,000명·100만 건은 서로 다른 시나리오이므로 시나리오 간 수치를 직접 비교하지 않습니다.", 20, "#617084")
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
canvas.save(OUTPUT, quality=96, subsampling=0)
print(OUTPUT)
