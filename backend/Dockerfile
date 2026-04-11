# Build: docker build -t divehub-api ./backend
# Run: see docker-compose.yml

FROM node:20-bookworm-slim AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-bookworm-slim AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=builder /app/dist ./dist
RUN mkdir -p /app/uploads/media
EXPOSE 3000
# Именованный том в docker-compose монтируется на /app/uploads — на VPS задайте non-root + chown при необходимости.
CMD ["node", "dist/main.js"]
