# 🚀 Tourding 배포 가이드

## 📋 사전 준비사항

### 1. AWS 계정 설정
- AWS CLI 설치 및 설정
- ECR, ECS, EC2 서비스 접근 권한
- IAM 사용자 생성 (ECR, ECS 권한 필요)

### 2. GitHub Secrets 설정
GitHub 저장소의 Settings > Secrets and variables > Actions에서 다음 설정:

```
AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret_key
```

### 3. AWS 리소스 생성
- ECR 저장소: `tourding-app`
- ECS 클러스터: `tourding-cluster`
- ECS 서비스: `tourding-service`
- ECS 태스크 정의: `tourding-task`

## 🐳 로컬 도커 테스트

### 빌드 및 실행
```bash
# 도커 이미지 빌드
docker build -t tourding-app .

# 도커 컴포즈로 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f tourding-app
```

### 접속 테스트
- http://localhost:8080

## 🔄 자동 배포 파이프라인

### 1. main 브랜치에 푸시
```bash
git add .
git commit -m "feat: 새로운 기능 추가"
git push origin main
```

### 2. GitHub Actions 자동 실행
- 코드 푸시 시 자동으로 GitHub Actions 실행
- Gradle 빌드 → 도커 이미지 생성 → ECR 푸시 → ECS 배포

### 3. 배포 상태 확인
- GitHub Actions 탭에서 배포 진행 상황 확인
- AWS ECS 콘솔에서 서비스 상태 확인

## 🛠️ 수동 배포

### GitHub Actions 수동 실행
1. GitHub 저장소 → Actions 탭
2. "Deploy to EC2" 워크플로우 선택
3. "Run workflow" 버튼 클릭
4. main 브랜치 선택 후 실행

## 📊 모니터링

### 헬스체크
- 애플리케이션 헬스체크: `/actuator/health`
- 도커 헬스체크: 30초마다 자동 실행

### 로그 확인
```bash
# ECS 태스크 로그
aws logs tail /ecs/tourding-service --follow

# 도커 컨테이너 로그
docker logs <container_id>
```

## 🔧 문제 해결

### 일반적인 문제들
1. **빌드 실패**: Gradle 의존성 문제 확인
2. **도커 빌드 실패**: Dockerfile 문법 확인
3. **배포 실패**: AWS 권한 및 ECS 설정 확인
4. **애플리케이션 시작 실패**: 환경변수 및 포트 설정 확인

### 디버깅 명령어
```bash
# 도커 이미지 확인
docker images

# 실행 중인 컨테이너 확인
docker ps

# 컨테이너 상세 정보
docker inspect <container_id>

# ECS 서비스 상태 확인
aws ecs describe-services --cluster tourding-cluster --services tourding-service
```

## 📝 환경별 설정

### 개발 환경
- `SPRING_PROFILES_ACTIVE=dev`
- 로컬 데이터베이스 사용

### 도커 환경
- `SPRING_PROFILES_ACTIVE=docker`
- 환경변수로 설정 주입

### 프로덕션 환경
- `SPRING_PROFILES_ACTIVE=prod`
- AWS Secrets Manager 사용 권장
