"""Place two genuine 4,000-user Grafana panel captures under a fair-load table."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


RESULTS = Path(__file__).resolve().parent / "results" / "drop-list-cache"
FONT = "C:/Windows/Fonts/malgun.ttf"
CAPTURES = [
    ("grafana-opening-4000-first.png", (16, 175, 695, 466)),
    ("grafana-opening-4000-second.png", (16, 195, 695, 478)),
]


def main():
    image = Image.new("RGB", (1410, 756), "#f8fafc")
    draw = ImageDraw.Draw(image)
    title = ImageFont.truetype(FONT, 29)
    body = ImageFont.truetype(FONT, 19)
    small = ImageFont.truetype(FONT, 15)

    draw.text((25, 20), "Grafana | 판매 시작 4,000명 Drop 목록 동시 조회", fill="#172b42", font=title)
    draw.text((25, 66), "같은 API · Spring 2대 · Nginx · 같은 JAR · 각 사용자 1회 조회 · 단계별 2회 측정", fill="#475569", font=body)

    draw.rounded_rectangle((20, 110, 1390, 320), radius=12, fill="#ffffff", outline="#dbe3eb")
    draw.text((42, 127), "캐시 단계", fill="#475569", font=body)
    draw.text((770, 127), "1차 p95", fill="#475569", font=body)
    draw.text((1080, 127), "2차 p95", fill="#475569", font=body)
    rows = [
        ("Redis 적용 전 (DB 직접 조회)", "미실행", "미실행", "#9a554b"),
        ("Redis 적용 후", "8.94초", "8.51초", "#17648d"),
        ("Redis + 서버별 L1 (200ms)", "12.06초", "12.36초", "#986900"),
    ]
    for index, (stage, first, second, color) in enumerate(rows):
        y = 166 + index * 47
        draw.text((42, y), stage, fill=color, font=body)
        draw.text((770, y), first, fill=color, font=body)
        draw.text((1080, y), second, fill=color, font=body)

    draw.text((28, 331), "L1 추가 후 Redis만 사용할 때보다 1차 34.91%, 2차 45.27% 느림", fill="#9a554b", font=body)
    draw.text((28, 363), "두 단계 모두 4,000/4,000건 성공 · 실패 0건. 4,000명 단발 조회에서 L1 개선 근거 없음.", fill="#475569", font=small)

    for index, (name, crop) in enumerate(CAPTURES):
        panel = Image.open(RESULTS / name).convert("RGB").crop(crop)
        image.paste(panel, (20 + index * 700, 401))

    draw.text((28, 704), "Grafana 실제 패널 캡처를 배치했습니다. 그래프 값은 저장된 k6 요약을 InfluxDB로 가져온 실측값입니다.", fill="#475569", font=small)
    draw.text((28, 728), "DB 직접 조회는 50명에서도 p95 약 20초여서 4,000명은 로컬 PC 안전 기준으로 실행하지 않았습니다. 부하가 다른 결과끼리 개선율을 계산하지 않았습니다.", fill="#475569", font=small)
    image.save(RESULTS / "grafana-opening-4000-before-after.jpg", quality=95, subsampling=0)


if __name__ == "__main__":
    main()
