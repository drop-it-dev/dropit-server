# DROPIT Server

<img width="640" height="320" alt="dropit_social" src="https://github.com/user-attachments/assets/12f97558-653b-4873-a8fc-00c3ee1371c2" />

> 정해진 시간, 한정된 수량으로 만나는 드랍 커머스

## 프로젝트 소개

**DROPIT은 판매자가 정한 시간에 한정 수량의 상품을 판매하는 드랍 커머스 서비스입니다.** 이 저장소는 회원·상품·드랍·주문을 처리하는 백엔드 API를 제공합니다.

판매자는 상품을 등록한 뒤 판매 기간, 가격, 재고, 1인당 구매 한도를 설정해 드랍을 엽니다. 구매자는 드랍을 탐색하고 판매 시간에 주문하며, 내 주문을 조회하거나 취소할 수 있습니다. 같은 상품으로 여러 번의 드랍을 열 수 있어 상품 정보와 개별 판매를 분리해 관리합니다.

## 프로젝트 목표

<!-- 이 프로젝트가 해결하려는 문제와 핵심 목표를 2~3개로 작성하세요. -->

_작성 예정_

## 주요 기능

_추가 예정_
| 사용자 | 주요 기능 |
| --- | --- |
| 공통 | 회원가입·로그인·토큰 재발급, 내 정보 수정 |
| 판매자 | 판매자 프로필 관리, 상품 등록·수정·삭제, 드랍의 판매 조건 및 공개 여부 관리 |
| 구매자 | 드랍 검색·정렬·목록 조회, 판매자별 드랍 조회, 드랍 주문, 내 주문 조회·취소 |

현재 주문 한 건은 하나의 드랍을 대상으로 합니다. 주문 시 판매 가능 여부·잔여 재고·누적 구매 한도를 검사하고, 취소 시 재고를 복원합니다. 현재 범위는 주문 생성과 취소까지이며 결제 승인은 포함하지 않습니다.

## 기술 스택

![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)
![JPA](https://img.shields.io/badge/JPA-Hibernate-59666C?style=flat-square&logo=hibernate&logoColor=white)
![QueryDSL](https://img.shields.io/badge/QueryDSL-7.5-0769AD?style=flat-square&logo=java&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.4-DC382D?style=flat-square&logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white)

| 구분 | 사용 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.1.1, Spring MVC, Spring Security, Spring Data JPA |
| 로컬 DB / Redis | MySQL 8.4, Redis 7.4 |
| Authentication | JWT (`jjwt`) |
| Query | QueryDSL |
| Build / test | Gradle 9.7.1, JUnit, Mockito, Testcontainers |
| 빌드·배포 환경 | Docker, Docker Compose, GitHub Actions, AWS |

### AWS 구성

프로젝트에서 사용하는 AWS 서비스와 팀 접근 관리 구성입니다.

| 구분 | 서비스 |
| --- | --- |
| 서버·네트워크 | EC2, VPC |
| 데이터·스토리지 | RDS, ElastiCache Valkey, S3 |
| 메시징 | SQS |
| 이미지 저장·설정 관리 | ECR, Systems Manager Parameter Store |
| 팀 접근 권한 | IAM 사용자 및 User Group 기반 권한 관리 |


## 시스템 아키텍처

<!-- 시스템 또는 AWS 인프라 아키텍처 이미지 삽입
![DROPIT 시스템 아키텍처](이미지_URL)
-->

_이미지 추가 예정_

## ERD
> [ERD Docs 바로가기](https://dbdocs.io/ayoooo240/dropit?view=relationships)

![DROPIT ERD](https://github.com/user-attachments/assets/d3c1b4be-1189-4695-9b9c-acc907020c44)

## 핵심 용어 ##

| 용어 | 의미 |
| --- | --- |
| 상품(Product) | 판매자가 등록한 판매 대상. 같은 상품으로 여러 드랍을 열 수 있습니다. |
| 드랍(Drop) | 특정 상품의 판매 기간·가격·재고·구매 한도를 정한 판매 단위입니다. |
| 판매자(Seller) | 상품과 드랍을 관리하는 사용자. 판매자 프로필(SellerProfile)은 판매자 부가 정보입니다. |
| 구매 한도(Purchase limit) | 사용자 한 명이 한 드랍에서 구매할 수 있는 누적 수량의 상한. `0`은 무제한이며, 취소한 주문 수량은 제외합니다. |
| 주문(Order) | 구매자와 총 주문 금액, 주문 상태를 담는 구매 기록입니다. 주문 생성은 결제 승인을 의미하지 않습니다. |
| 주문 항목(Order item) | 주문한 드랍·수량과 주문 당시 상품명·가격을 저장하는 기록. 현재 주문마다 항목 하나를 가집니다. |
| 주문 취소(Cancellation) | 생성된 주문을 취소하고 해당 수량의 재고와 구매 한도를 복원하는 행위입니다. |

## 프로젝트 구조

```text
src/main/java/com/dropit
├── auth/           # 인증과 토큰 발급
├── user/           # 사용자
├── sellerprofile/  # 판매자 부가 정보
├── product/        # 상품
├── drop/           # 드랍 판매
├── order/          # 주문과 주문 항목
└── global/         # 보안, 예외, 공통 엔티티와 설정
```

## API 문서

Spring REST Docs를 사용하고, 테스트 스니펫을 OpenAPI 3.0.1 명세로 변환합니다.
Swagger UI는 이 명세 파일을 읽어 화면에 보여주는 역할만 하므로, 별도의 어노테이션 기반 API 명세가 생기지 않습니다.

`bootRun`과 `bootJar`는 테스트와 OpenAPI 생성 작업을 먼저 실행한 뒤 생성된 YAML을 애플리케이션 정적 리소스에 포함합니다.
명세와 Swagger UI 리소스를 단독으로 준비하려면 `./gradlew openApiDocs`를 실행합니다.
명세만 생성하려면 `./gradlew openapi3`를 실행하며, 결과는 `build/api-spec/openapi3.yaml`에 저장됩니다. (생성물은 빌드 디렉터리에만 남으며 저장소에는 커밋하지 않습니다.)

브라우저에서 http://localhost:8080/swagger-ui/index.html를 열면 API 문서를 확인할 수 있습니다.
원본 명세는 http://localhost:8080/openapi/openapi3.yaml 경로에서도 직접 확인할 수 있습니다.

Swagger UI와 OpenAPI YAML은 HTTP Basic Auth로 보호됩니다.
로컬에서는 `.env.local`에 다음 두 값을 설정합니다.

```properties
DOCS_AUTH_USERNAME=<local username>
DOCS_AUTH_PASSWORD=<local password>
```

인증이 필요한 API는 다음 헤더를 전달합니다.

```http
Authorization: Bearer {accessToken}
```

## 프로젝트 주요 경험

_추가 예정_

| 주제 | 구현 내용 |
| --- | --- |
| 한정 판매 상태 관리 | 드랍의 판매 시작·종료 시각과 잔여 재고를 기준으로 `READY`, `OPEN`, `SOLDOUT`, `CLOSED` 상태를 계산합니다. 판매 정보 수정·삭제는 판매 시작 전에만 허용합니다. |
| 주문 금액과 상품 정보 보존 | `OrderItem`에 주문 시점의 상품명·정가·할인율·수량·최종 금액을 저장해, 이후 상품 정보가 바뀌어도 과거 주문을 재현할 수 있게 했습니다. |
| JWT 기반 API 보안 | 로그인·회원가입·토큰 재발급과 JWT 필터를 구성하고, 인증 주체를 `@CurrentUserId`로 받아 리소스 소유권을 확인합니다. |
| AWS 배포 및 환경 설정 | GitHub Actions에서 테스트·이미지 빌드·ECR 업로드와 EC2 배포를 연결하고, 운영 설정을 Parameter Store에서 읽도록 구성했습니다. |
| 팀 AWS 접근 권한 관리 | IAM User Group을 만들고 팀원별 IAM 사용자를 그룹에 연결해, 그룹 정책으로 팀의 AWS 접근 권한을 관리했습니다. |


## 기술적 의사결정과 트러블 슈팅

### 기술적 의사결정

<!-- 선택한 기술, 검토한 대안, 선택 이유와 결과를 링크 또는 요약으로 작성 -->

_작성 예정_

### 트러블 슈팅

<!-- 문제 상황, 원인, 해결 과정과 결과가 드러나는 글을 링크하거나 요약 -->

_작성 예정_

## 시작하기

### 요구 사항

- JDK 21
- Docker 및 Docker Compose

### 1. 환경 변수 파일 준비

필수 항목은 다음과 같습니다.

```properties
DB_HOST=localhost
DB_PORT=3306
DB_NAME=dropit
DB_USERNAME=dropit
DB_PASSWORD=local-password
MYSQL_ROOT_PASSWORD=local-root-password
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=change-this-to-a-long-random-secret
```

### 2. MySQL과 Redis 실행

```bash
./gradlew dbUp
```

`dbUp`은 `compose.yml`의 MySQL 8.4와 Redis 7.4 컨테이너를 백그라운드로 시작합니다. 종료할 때는 다음 명령을 사용합니다.

```bash
./gradlew dbDown
```

### 3. 애플리케이션 실행

로컬 프로필은 `.env.local`을 Spring 설정으로 읽습니다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

기본 포트는 `8080`이며, 상태 확인 엔드포인트는 인증 없이 접근할 수 있습니다.

```bash
curl http://localhost:8080/actuator/health
```

## 설정 프로필

| 프로필 | 용도 | 설정 방식 |
| --- | --- | --- |
| `local` | 개인 개발 환경 | `.env.local` 파일을 선택적으로 import |
| `prod` | 운영 환경 | AWS Parameter Store의 `/dropit-server/prod/` 경로 import |


## CI/CD

`main` 대상 Pull Request에서는 테스트와 JAR 빌드만 수행합니다. PR이 병합되어 `main`에 push되면, build job에서 생성한 JAR artifact를 사용해 AWS OIDC 인증을 거쳐 Docker 이미지를 ECR에 업로드하고, Launch Template 갱신과 Auto Scaling Group Instance Refresh로 배포합니다.

운영 배포에 필요한 AWS 변수와 권한은 GitHub Actions 및 AWS 환경에서 별도로 관리합니다. 비밀값은 저장소나 README에 기록하지 않습니다.

## 팀원 및 역할

<!-- 이름, 역할, 담당 기능과 GitHub 링크를 작성 -->

| 이름 | 역할 | 담당 영역 | GitHub |
| --- | --- | --- | --- |
|  |  |  |  |

## 협업 방식

<!-- 브랜치 전략, 코드 리뷰, 이슈 관리, 커뮤니케이션 방식 등을 작성 -->

_작성 예정_
