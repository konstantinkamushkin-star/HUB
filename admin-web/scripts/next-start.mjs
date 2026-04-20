#!/usr/bin/env node
/**
 * Старт Next в prod: порт из переменной PORT (как у Next), иначе 3001.
 * Обходит ограничения npm/sh с подстановкой ${PORT:-...} в package.json.
 */
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const port = String(process.env.PORT ?? "3001");
const root = path.join(path.dirname(fileURLToPath(import.meta.url)), "..");

const r = spawnSync("npx", ["next", "start", "-p", port], {
  cwd: root,
  stdio: "inherit",
  shell: true,
  env: process.env,
});

process.exit(r.status === null ? 1 : r.status);
