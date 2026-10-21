# syntax=docker/dockerfile:1

ARG NODE_VERSION=24-slim

# ============================================
# Stage 1: 依存関係インストール
# ============================================
FROM node:${NODE_VERSION} AS deps
WORKDIR /app

# package.json / package-lock.json のみ先にコピーし、依存関係が変わらない限り
# レイヤーキャッシュを再利用できるようにする
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

# ============================================
# Stage 2: ビルド
# ============================================
FROM node:${NODE_VERSION} AS builder
WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# NEXT_PUBLIC_ プレフィックスの環境変数はビルド時にクライアントバンドルへ
# 埋め込まれるため、ビルド引数として受け取れるようにしておく
ARG NEXT_PUBLIC_API_BASE_URI
ARG NEXT_PUBLIC_USE_RELATIVE_API_URL
ENV NEXT_PUBLIC_API_BASE_URI=${NEXT_PUBLIC_API_BASE_URI}
ENV NEXT_PUBLIC_USE_RELATIVE_API_URL=${NEXT_PUBLIC_USE_RELATIVE_API_URL}
ENV NEXT_TELEMETRY_DISABLED=1

RUN mkdir -p public
RUN npm run build

# ============================================
# Stage 3: 実行
# ============================================
FROM node:${NODE_VERSION} AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

COPY --from=builder --chown=node:node /app/public ./public

# prerender cache 用に .next を node ユーザー所有で作成しておく
RUN mkdir .next && chown node:node .next

# standalone 出力（実行に必要なファイルのみ）をコピーしてイメージを軽量化する
# https://nextjs.org/docs/app/api-reference/config/next-config-js/output
COPY --from=builder --chown=node:node /app/.next/standalone ./
COPY --from=builder --chown=node:node /app/.next/static ./.next/static

# 非rootユーザーで実行する
USER node

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "fetch('http://localhost:3000/').then((r) => process.exit(r.status >= 500 ? 1 : 0)).catch(() => process.exit(1))"

CMD ["node", "server.js"]
