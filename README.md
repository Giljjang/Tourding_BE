# Tourding Project

## 환경 설정

### 1. 환경변수 설정

프로젝트를 실행하기 전에 다음 환경변수들을 설정해야 합니다:

```bash
# API 키
export NAVER_CLIENT_ID=your_naver_client_id_here
export NAVER_CLIENT_SECRET=your_naver_client_secret_here
export TOUR_CLIENT_SERVICEKEY=your_tour_api_key_here

# 데이터베이스 설정
export DATABASE_URL=jdbc:mysql://localhost:3306/tourding?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useUnicode=true
export DATABASE_USERNAME=admin
export DATABASE_PASSWORD=your_password_here
```

### 2. 환경변수 파일 생성 (권장)

프로젝트 루트에 `.env` 파일을 생성하고 위의 환경변수들을 설정하세요:

```bash
cp env.example .env
# .env 파일을 편집하여 실제 값들을 입력
```

### 3. 실행 방법

#### 로컬 실행
```bash
./gradlew bootRun
```

#### Docker 실행
```bash
docker-compose up -d
```

## 주의사항

- **절대 `.env` 파일을 Git에 커밋하지 마세요!**
- 모든 민감한 정보는 환경변수로 관리됩니다
- `application.yml` 하나의 파일로 모든 설정을 관리합니다
