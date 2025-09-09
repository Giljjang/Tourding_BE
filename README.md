# Tourding Project

## 🚀 자동 배포 시스템

이 프로젝트는 GitHub Actions를 통해 Azure VM에 자동 배포됩니다.

### 📋 배포 정보
- **서버**: Azure VM
- **데이터베이스**: Azure SQL Server
- **배포 브랜치**: main, develop
- **애플리케이션 URL**: http://[서버IP]:8080
- **Swagger 문서**: http://[서버IP]:8080/swagger-ui.html

### 🔧 Azure VM 초기 설정

Azure VM에서 다음 스크립트를 실행하여 Docker를 설치하세요:

```bash
# Azure VM에 접속 후 실행
curl -fsSL https://raw.githubusercontent.com/your-repo/tourding/main/scripts/setup-azure-server.sh | bash
```

또는 수동으로 스크립트를 다운로드하여 실행:

```bash
wget https://raw.githubusercontent.com/your-repo/tourding/main/scripts/setup-azure-server.sh
chmod +x setup-azure-server.sh
./setup-azure-server.sh
```

### 🔑 GitHub Secrets 설정

GitHub 저장소의 Settings > Secrets and variables > Actions에서 다음 시크릿을 설정하세요:

- `AZURE_HOST`: [서버 IP 주소]
- `AZURE_USER`: [VM 사용자명]
- `AZURE_SSH_KEY`: [SSH 개인키]

### 🐳 Docker 환경

#### 로컬 개발 환경
```bash
# 환경변수 파일 복사
cp env.example .env

# Docker Compose로 실행
docker-compose up -d
```

#### 프로덕션 환경
- GitHub Actions가 자동으로 Azure VM에 배포
- `env.example` 파일이 자동으로 `.env`로 복사됨
- SQL Server 데이터베이스 연결

## 🛠️ 로컬 개발 환경 설정

### 1. 환경변수 설정

프로젝트 루트에 `.env` 파일을 생성하세요:

```bash
cp env.example .env
# .env 파일을 편집하여 실제 값들을 입력
```

### 2. 실행 방법

#### 로컬 실행
```bash
./gradlew bootRun
```

#### Docker 실행
```bash
docker-compose up -d
```

## API 문서 (Swagger)

애플리케이션이 실행된 후 다음 URL에서 API 문서를 확인할 수 있습니다:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **API Docs**: http://localhost:8080/api-docs

### 주요 API 엔드포인트

#### Tour API
- `GET /tour/search-keyword` - 키워드로 관광지 검색

#### Route API  
- `POST /routes` - 출발지, 도착지 입력해서 길찾기
- `GET /routes/guide` - 사용자 ID로 경로 안내 조회
- `GET /routes/path` - 사용자 ID로 경로 조회
- `GET /routes/section` - 사용자 ID로 경로 구간 조회

#### Health Check
- `GET /health` - 애플리케이션 상태 확인

## 주의사항

- **절대 `.env` 파일을 Git에 커밋하지 마세요!**
- 모든 민감한 정보는 환경변수로 관리됩니다
- `application.yml` 하나의 파일로 모든 설정을 관리합니다
