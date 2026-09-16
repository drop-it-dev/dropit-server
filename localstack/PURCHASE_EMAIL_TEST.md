# 구매 완료 자동 이메일 로컬 테스트

이 테스트는 가짜 이메일 전용 API를 호출하지 않습니다. 실제 구매 API부터 시작해 다음 흐름 전체를 확인합니다.

```text
POST /orders
  -> Redis 재고 선점
  -> 주문 SQS
  -> 주문 생성
  -> 이메일 Outbox
  -> 이메일 SQS
  -> SES 이메일 발송
```

LocalStack의 SES는 인터넷으로 메일을 보내는 대신 로컬 보관함에 결과를 저장합니다.

## 1. 로컬 인프라 준비

프로젝트 루트의 PowerShell에서 실행합니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\localstack\start-purchase-email-test.ps1
```

이 스크립트는 MySQL, Redis, LocalStack을 시작하고 주문·이메일 SQS와 각 DLQ를 준비합니다. 평소 로컬 데이터 보호를 위해 `dropit_localstack` DB를 따로 사용합니다.

## 2. Spring 서버 실행

IntelliJ 실행 구성의 Active profiles에 다음 값을 입력한 뒤 서버를 실행합니다.

```text
local,localstack
```

8080 포트를 다른 프로그램이 사용 중이면 VM 옵션 또는 Program arguments에 다음 값을 추가합니다.

```text
--server.port=18080
```

이 경우 Postman 컬렉션의 `baseUrl`도 `http://localhost:18080`으로 변경합니다.

## 3. Postman에서 전체 흐름 실행

다음 파일을 Postman에서 Import 합니다.

```text
postman/dropit-purchase-email-local.postman_collection.json
```

컬렉션의 **Run collection**을 누르고 Runner의 요청 간 지연(Delay)을 **300ms**로 설정한 뒤 1번부터 10번까지 순서대로 실행합니다. 이 지연은 9번과 10번의 폴링 요청이 서버를 과도하게 호출하지 않도록 합니다. 컬렉션이 판매자, 상품, Drop, 구매자를 자동으로 만든 뒤 실제 구매 요청을 보냅니다.

마지막 10번 요청의 **Visualize** 탭에서 다음 시간을 확인할 수 있습니다.

| 지표 | 의미 |
|---|---|
| 구매 API 접수 | `POST /orders`가 Redis 선점과 SQS 발행을 접수하고 202를 반환한 시간 |
| 주문 생성 완료 | 비동기 주문 Consumer가 DB 주문을 생성할 때까지의 누적 시간 |
| 이메일 발송 완료 | Outbox와 이메일 SQS를 거쳐 SES가 이메일을 수락할 때까지의 누적 시간 |

로컬 SES 원문은 브라우저에서도 확인할 수 있습니다.

```text
http://localhost:4566/_aws/ses
```

## 측정 결과를 읽을 때 주의할 점

- Postman 결과는 한 번의 기능·지연 시간 확인입니다. 서버 처리량을 나타내는 부하 테스트 결과는 아닙니다.
- 대량 부하 테스트에서는 구매 API 접수 성능과 이메일 Consumer 처리량을 따로 측정해야 합니다.
- 실제 AWS 검증에서는 IAM 권한, SES Sandbox, 검증된 발신 주소, 실제 받은편지함 전달 여부를 추가로 확인해야 합니다.
