"""Join four genuine Grafana panel screenshots into one review image.

The measured bars and numbers remain untouched. The header and footer only
describe the comparisons and the saved-k6-summary provenance.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


RESULTS = Path(__file__).resolve().parent / "results" / "drop-list-cache"
PANELS = [
    ("grafana-opening-first.png", (16, 100, 695, 392)),
    ("grafana-opening-second.png", (16, 165, 695, 457)),
    ("grafana-million-p95.png", (16, 135, 695, 427)),
    ("grafana-million-failures.png", (16, 160, 695, 452)),
]
FONT_PATH = "C:/Windows/Fonts/malgun.ttf"


def main():
    panels = [Image.open(RESULTS / name).convert("RGB").crop(box) for name, box in PANELS]
    canvas = Image.new("RGB", (1410, 766), "#f8fafc")
    draw = ImageDraw.Draw(canvas)
    headline = ImageFont.truetype(FONT_PATH, 25)
    body = ImageFont.truetype(FONT_PATH, 15)
    draw.text((22, 17), "Grafana | Drop 목록 Redis 적용 전후 실제 k6 비교", fill="#172b42", font=headline)
    draw.text((22, 55), "50명 동시 조회: Redis 후 p95 99.68% / 99.49% 감소", fill="#17648d", font=body)
    draw.text((715, 55), "100만 건: L1 추가 후 p95 77.15% 감소 · 실패 38,154 → 0", fill="#17648d", font=body)

    positions = [(14, 92), (716, 92), (14, 403), (716, 403)]
    for panel, position in zip(panels, positions):
        canvas.paste(panel, position)

    draw.text(
        (22, 724),
        "Grafana 화면 캡처 4개를 배치한 이미지. 그래프 값은 저장된 k6 요약을 InfluxDB로 가져온 실측값입니다.",
        fill="#475569",
        font=body,
    )
    draw.text(
        (22, 745),
        "Redis 적용 전 100만 건 지속 조회는 로컬 PC 과부하 위험으로 실행하지 않았습니다. L1의 50명 단발 효과는 일관되지 않았습니다.",
        fill="#475569",
        font=body,
    )
    canvas.save(RESULTS / "grafana-redis-before-after.jpg", quality=95, subsampling=0)


if __name__ == "__main__":
    main()
