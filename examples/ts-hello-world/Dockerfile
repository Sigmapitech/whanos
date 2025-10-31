######## Builder: compile TypeScript and install deps
FROM whanos-javascript AS builder
WORKDIR /src

# Install dependencies (including dev) for build
COPY --chown=nixuser:nixuser package*.json tsconfig.json ./
# Install deps from lockfile; install TypeScript locally without touching package.json
RUN npm ci && npm install --no-save typescript@4.4.3

# Copy sources and build
COPY --chown=nixuser:nixuser app ./app
RUN npx tsc

# Prune devDependencies to keep runtime small
RUN npm prune --omit=dev

######## Runtime: only runtime deps + compiled JS
FROM whanos-javascript
ENV NODE_ENV=production
WORKDIR /app

# Copy compiled app and runtime deps
COPY --from=builder /src/app ./app
COPY --from=builder /src/node_modules ./node_modules

EXPOSE 3000
CMD ["node", "app/app.js"]
