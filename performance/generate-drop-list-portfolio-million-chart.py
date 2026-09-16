"""Create a JPEG comparison from the two matched 18-minute k6 summaries."""

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
RAW = ROOT / "results" / "raw"
OUTPUT = ROOT / "results" / "drop-list-cache" / "portfolio-million-before-after.jpg"
FONT = Path("C:/Windows/Fonts/malgun.ttf")


def load(name):
    with (RAW / name).open(encoding="utf-8") as source:
        metrics = json.load(source)["metrics"]
    return {
        "success": metrics["checks"]["values"]["passes"],
        "failed": metrics["checks"]["values"]["fails"],
        "dropped": metrics["dropped_iterations"]["values"]["count"],
        "p95": metrics["http_req_duration"]["values"]["p(95)"],
    }


before = load("portfolio-million-redis-1000rps-sep15.json")
after = load("portfolio-million-local-1000rps-sep15.json")
image = Image.new("RGB", (1600, 1250), "#ffffff")
draw = ImageDraw.Draw(image)


def label(x, y, value, size=25, color="#1b2c3c"):
    draw.text((x, y), value, font=ImageFont.truetype(str(FONT), size), fill=color)


def bar(y, title, value, limit, color, value_label):
    label(75, y + 8, title, 26)
    draw.rounded_rectangle((450, y, 1300, y + 51), radius=13, fill="#e8edf2")
    width = max(8, round(850 * value / max(limit, 1)))
    draw.rounded_rectangle((450, y, 450 + width, y + 51), radius=13, fill=color)
    label(1325, y + 7, value_label, 24)


label(70, 50, "Drop 목록 100만 조회: Redis만 / Redis + L1", 42)
label(70, 111, "같은 코드 · 서버 2대 · 1,000 RPS · 18분 · k6 시계열 기록 OFF", 24, "#617084")
label(70, 170, "GET /drops?sortType=LATEST&page=0&size=20", 25, "#36577c")

red, blue = "#c97465", "#307dbb"

label(75, 252, "실제 200 응답 수 (많을수록 좋음)", 29)
bar(315, "Redis만 사용", before["success"], 1080000, red, f'{before["success"]:,}건')
bar(395, "Redis + L1", after["success"], 1080000, blue, f'{after["success"]:,}건')
success_gain = after["success"] - before["success"]
label(450, 461, f'정상 응답 +{success_gain:,}건 ({100 * success_gain / before["success"]:.2f}% 증가)', 23, "#286b9e")

label(75, 505, "시작 못 한 요청 수 (적을수록 좋음)", 29)
drop_limit = max(before["dropped"], after["dropped"], 1)
bar(567, "Redis만 사용", before["dropped"], drop_limit, red, f'{before["dropped"]:,}건')
bar(647, "Redis + L1", after["dropped"], drop_limit, blue, f'{after["dropped"]:,}건')
dropped_reduction = before["dropped"] - after["dropped"]
label(450, 713, f'시작 못 한 요청 -{dropped_reduction:,}건 ({100 * dropped_reduction / before["dropped"]:.2f}% 감소)', 23, "#286b9e")

label(75, 755, "p95 응답 시간 (짧을수록 좋음)", 29)
p95_limit = max(before["p95"], after["p95"], 1)
bar(817, "Redis만 사용", before["p95"], p95_limit, red, f'{before["p95"]:.2f}ms')
bar(897, "Redis + L1", after["p95"], p95_limit, blue, f'{after["p95"]:.2f}ms')
p95_reduction = before["p95"] - after["p95"]
label(450, 963, f'p95 -{p95_reduction:.2f}ms ({100 * p95_reduction / before["p95"]:.2f}% 감소)', 23, "#286b9e")

draw.rounded_rectangle((70, 1040, 1530, 1160), radius=16, fill="#f1f5f9")
label(95, 1058, f'HTTP 실패: {before["failed"]:,}건 → {after["failed"]:,}건 (100% 감소)', 27)
label(95, 1111, "서버별 200ms L1에는 정적 목록만 보관 · 재고는 매 요청 Redis MGET", 22)
label(75, 1182, "로컬 PC 각 1회 측정. 108만 예약 요청과 실제 성공 조회는 구분해야 합니다.", 19, "#617084")

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
image.save(OUTPUT, quality=92, subsampling=0)
print(OUTPUT)
