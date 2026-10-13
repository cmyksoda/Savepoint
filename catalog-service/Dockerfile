FROM node:22-alpine
WORKDIR /app
COPY --chown=node:node package.json server.mjs covers.mjs ./
USER node
ENV NODE_ENV=production HOST=127.0.0.1 PORT=8787
CMD ["node", "server.mjs"]
