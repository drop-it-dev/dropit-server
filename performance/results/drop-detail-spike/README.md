# Drop 상세 조회 판매 시작 스파이크 테스트

## 테스트 목적

판매 시작 시각에 사용자가 동일한 Drop 상세 페이지로 몰리는 상황을 재현하고,
현재 JPA 직접 조회 구조의 처리 한계를 Redis Cache 적용 전 기준으로 기록한다.

대상 API:

```http
GET /drops/{dropId}
```

## Before 테스트 조건

| 항목 | 값 |
|---|---:|
| 측정일 | 2026-09-12 |
| 대상 Drop ID | 1,262,071 |
| 평상시 부하 | 100 RPS, 30초 |
| 스파이크 | 1초 안에 1,000 RPS |
| 스파이크 유지 | 1분 |
| 최대 VU | 2,000 |
| 실행 환경 | Windows 로컬 Spring 서버, Docker k6·InfluxDB·Grafana·MySQL |

## Before 결과

| 지표 | Before |
|---|---:|
| 완료 HTTP 요청 | 40,084건 |
| 실제 평균 처리량 | 377.31 RPS |
| 평균 응답시간 | 3,428.22ms |
| p95 응답시간 | 5,177.81ms |
| p99 응답시간 | 5,573.63ms |
| 최대 응답시간 | 6,934.71ms |
| HTTP 실패율 | 0% |
| 응답 검증 성공률 | 100% |
| 시작하지 못한 요청 | 30,965건 |
| 사용된 최대 VU | 2,000 |

## 현재 코드 흐름

```text
k6 → JwtFilter → DropController.getOne()
   → DropService.getOne()
   → DropRepository.findById()
   → DropResponse.from()
   → HTTP 응답
```

`DropService.getOne()`은 요청마다 `DropRepository.findById()`를 호출한다.
같은 Drop을 반복 조회해도 애플리케이션 수준의 캐시는 아직 사용하지 않는다.

## 해석

- 서버 프로세스는 종료되지 않았고 완료된 HTTP 요청의 실패율은 0%였다.
- 목표 1,000 RPS를 유지하려고 k6가 최대 2,000 VU를 모두 사용했다.
- 실제 완료 처리량은 평균 377.31 RPS였으며 30,965회는 필요한 VU를 확보하지 못해 시작되지 않았다.
- p95가 목표 2초를 넘은 5.18초이므로 사용자 관점에서는 판매 시작 직후 심각한 지연이 발생한 상태다.
- 이번 결과만으로 DB·서버 스레드·CPU 중 주 병목을 확정할 수 없다. 다음 단계에서 서버 내부 지표를 수집한 뒤 개선 대상을 결정한다.

## 비교 원칙

Redis 적용 후에도 같은 Drop ID, 요청 단계, 실행 시간, 최대 VU, 인증 방식과 로컬 환경을 유지한다.
원본 k6 JSON은 `performance/results/raw`에 로컬로 저장하고 Git에는 포함하지 않는다.
