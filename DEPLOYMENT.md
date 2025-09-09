# 🚀 Tourding 자동 배포 가이드

## 📋 개요

이 문서는 Tourding 프로젝트의 Azure VM 자동 배포 시스템 설정 및 사용 방법을 설명합니다.

## 🏗️ 아키텍처

```
GitHub Repository
       ↓ (GitHub Actions)
Azure VM ([서버 IP])
       ↓ (Docker)
Azure SQL Server (tourding.database.windows.net)
```

## 🔧 초기 설정

### 1. Azure VM 준비

Azure VM이 준비되어 있어야 합니다:
- **IP 주소**: [서버 IP 주소]
- **OS**: Ubuntu (권장)
- **SSH 접근 가능**

### 2. Azure VM에 Docker 설치

VM에 SSH로 접속한 후 다음 명령어를 실행하세요:

```bash
# 스크립트 다운로드 및 실행
curl -fsSL https://raw.githubusercontent.com/your-repo/tourding/main/scripts/setup-azure-server.sh | bash

# 또는 수동 다운로드
wget https://raw.githubusercontent.com/your-repo/tourding/main/scripts/setup-azure-server.sh
chmod +x setup-azure-server.sh
./setup-azure-server.sh
```

### 3. GitHub Secrets 설정

GitHub 저장소의 Settings > Secrets and variables > Actions에서 다음 시크릿을 설정:

| Secret Name | Value | 설명 |
|-------------|-------|------|
| `AZURE_HOST` | [서버 IP 주소] | Azure VM IP 주소 |
| `AZURE_USER` | [VM 사용자명] | Azure VM 사용자명 |
| `AZURE_SSH_KEY` | [SSH 개인키] | SSH 접속용 개인키 |

### 4. SSH 키 생성 및 설정

로컬에서 SSH 키를 생성하고 Azure VM에 공개키를 등록:

```bash
# SSH 키 생성 (이미 있다면 생략)
ssh-keygen -t rsa -b 4096 -C "your-email@example.com"

# 공개키를 Azure VM에 복사
ssh-copy-id username@[서버IP]

# 또는 수동으로 복사
cat ~/.ssh/id_rsa.pub
# 위 내용을 Azure VM의 ~/.ssh/authorized_keys에 추가
```

## 🚀 배포 프로세스

### 자동 배포

1. **코드 푸시**: main 또는 develop 브랜치에 코드를 푸시
2. **GitHub Actions 실행**: 자동으로 워크플로우가 시작됨
3. **빌드**: Gradle을 사용하여 애플리케이션 빌드
4. **파일 전송**: SCP를 통해 Azure VM에 파일 전송
5. **Docker 배포**: Docker Compose를 사용하여 컨테이너 실행

### 수동 배포

Azure VM에 직접 접속하여 수동으로 배포할 수도 있습니다:

```bash
# Azure VM에 접속
ssh username@[서버IP]

# 프로젝트 디렉토리로 이동
cd ~/tourding

# Git에서 최신 코드 가져오기
git pull origin main

# 환경변수 파일 생성
cp env.example .env

# Docker 컨테이너 재시작
docker-compose down
docker-compose up -d --build
```

## 🔍 배포 상태 확인

### 1. GitHub Actions 로그 확인

GitHub 저장소의 Actions 탭에서 배포 상태를 확인할 수 있습니다.

### 2. Azure VM에서 확인

```bash
# 컨테이너 상태 확인
docker-compose ps

# 애플리케이션 로그 확인
docker-compose logs -f tourding-app

# 헬스체크
curl http://localhost:8080/actuator/health
```

### 3. 외부에서 접근 확인

- **애플리케이션**: http://[서버IP]:8080
- **Swagger 문서**: http://[서버IP]:8080/swagger-ui.html
- **API 문서**: http://[서버IP]:8080/api-docs

## 🛠️ 문제 해결

### 일반적인 문제들

#### 1. Docker 권한 문제
```bash
# 사용자를 docker 그룹에 추가
sudo usermod -aG docker $USER
# 세션 재시작 필요
```

#### 2. 포트 충돌
```bash
# 8080 포트 사용 중인 프로세스 확인
sudo netstat -tlnp | grep :8080
# 프로세스 종료
sudo kill -9 [PID]
```

#### 3. 데이터베이스 연결 실패
- Azure SQL Server 방화벽 규칙 확인
- 연결 문자열 확인
- 네트워크 연결 상태 확인

#### 4. 메모리 부족
```bash
# Docker 메모리 사용량 확인
docker stats

# 불필요한 컨테이너/이미지 정리
docker system prune -a
```

### 로그 확인

```bash
# 애플리케이션 로그
docker-compose logs tourding-app

# 시스템 로그
journalctl -u docker.service

# 방화벽 상태
sudo ufw status
```

## 📊 모니터링

### 헬스체크 엔드포인트

- `GET /actuator/health` - 애플리케이션 상태
- `GET /actuator/info` - 애플리케이션 정보

### 리소스 모니터링

```bash
# 시스템 리소스 사용량
htop

# Docker 리소스 사용량
docker stats

# 디스크 사용량
df -h
```

## 🔒 보안 고려사항

1. **SSH 키 관리**: 정기적으로 SSH 키를 교체
2. **방화벽 설정**: 필요한 포트만 열기
3. **환경변수 보안**: 민감한 정보는 GitHub Secrets 사용
4. **정기 업데이트**: 시스템 및 Docker 이미지 정기 업데이트

## 📞 지원

문제가 발생하면 다음을 확인하세요:

1. GitHub Actions 로그
2. Azure VM 시스템 로그
3. Docker 컨테이너 로그
4. 네트워크 연결 상태

추가 도움이 필요하면 팀에 문의하세요.