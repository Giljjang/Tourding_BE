#!/bin/bash

# Azure VM 초기 설정 스크립트
# 이 스크립트는 Azure VM에서 한 번만 실행하면 됩니다.

echo "🚀 Azure VM 초기 설정을 시작합니다..."

# 시스템 업데이트
echo "📦 시스템 패키지 업데이트 중..."
sudo apt update && sudo apt upgrade -y

# 필수 패키지 설치
echo "🔧 필수 패키지 설치 중..."
sudo apt install -y curl wget git unzip

# Docker 설치
echo "🐳 Docker 설치 중..."
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Docker Compose 설치
echo "🔗 Docker Compose 설치 중..."
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 방화벽 설정 (8080 포트 열기)
echo "🔥 방화벽 설정 중..."
sudo ufw allow 8080/tcp
sudo ufw allow 22/tcp
sudo ufw --force enable

# 프로젝트 디렉토리 생성
echo "📁 프로젝트 디렉토리 생성 중..."
mkdir -p ~/tourding
cd ~/tourding

# Git 설정 (선택사항)
echo "🔑 Git 설정을 위해 다음 명령어를 실행하세요:"
echo "git config --global user.name 'Your Name'"
echo "git config --global user.email 'your.email@example.com'"

# Docker 서비스 시작
echo "🔄 Docker 서비스 시작 중..."
sudo systemctl start docker
sudo systemctl enable docker

# 설치 완료 메시지
echo "✅ Azure VM 초기 설정이 완료되었습니다!"
echo ""
echo "📋 다음 단계:"
echo "1. GitHub Actions에서 다음 시크릿을 설정하세요:"
echo "   - AZURE_HOST: 20.214.242.182"
echo "   - AZURE_USER: [VM 사용자명]"
echo "   - AZURE_SSH_KEY: [SSH 개인키]"
echo ""
echo "2. 프로젝트를 main 또는 develop 브랜치에 푸시하면 자동 배포됩니다."
echo ""
echo "3. 애플리케이션 접속: http://20.214.242.182:8080"
echo "4. Swagger 문서: http://20.214.242.182:8080/swagger-ui.html"
echo ""
echo "⚠️  현재 세션을 종료하고 다시 로그인하여 Docker 그룹 권한을 적용하세요."

# 스크립트 정리
rm -f get-docker.sh

echo "🎉 설정 완료!"
