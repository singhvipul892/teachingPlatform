import { execFileSync } from 'node:child_process';
import * as path from 'node:path';
import { request, test } from '@playwright/test';
import type { APIRequestContext } from '@playwright/test';
import { EPHEMERAL_ADMIN, EPHEMERAL_STUDENT, isEphemeral } from './config';

const COMPOSE_FILE = path.resolve(__dirname, '..', 'docker-compose.test.yml');
const BOOT_TIMEOUT_MS = 180_000;

test.describe.configure({ mode: 'serial' });

/**
 * Prepares the throwaway stack. Nothing here is idempotent-by-luck: the stack
 * starts from an empty tmpfs database every time, so a re-run after
 * `stack:down` always seeds cleanly. Re-running against a *live* stack is
 * tolerated (signup returns a conflict, which we ignore).
 */
test('seed the ephemeral stack', async ({ baseURL }) => {
  test.setTimeout(BOOT_TIMEOUT_MS + 30_000);

  if (!isEphemeral(baseURL)) {
    throw new Error(
      `Seeding refuses to run against ${baseURL}. It creates users and promotes one to ADMIN — ` +
        'that must never touch the real environment.',
    );
  }

  const api = await request.newContext({ baseURL });
  try {
    await waitForApi(api, baseURL!);
    await signup(api, EPHEMERAL_ADMIN);
    await signup(api, EPHEMERAL_STUDENT);
    promoteToAdmin(EPHEMERAL_ADMIN.email);
  } finally {
    await api.dispose();
  }
});

async function waitForApi(api: APIRequestContext, baseURL: string): Promise<void> {
  const deadline = Date.now() + BOOT_TIMEOUT_MS;
  let lastError = 'no attempt made';

  while (Date.now() < deadline) {
    try {
      const response = await api.get('/api/courses', { timeout: 5_000 });
      if (response.ok()) return;
      lastError = `HTTP ${response.status()}`;
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }

  throw new Error(
    `The ephemeral API at ${baseURL} never came up (last: ${lastError}).\n` +
      'Is Docker Desktop running? Start the stack with:  npm run stack:up\n' +
      'Then check its logs with:                          npm run stack:logs',
  );
}

async function signup(api: APIRequestContext, user: typeof EPHEMERAL_ADMIN): Promise<void> {
  const response = await api.post('/api/auth/signup', { data: user });
  // 409/400 means a previous run already created it; the account is what we
  // want either way. Anything else is a genuine failure worth surfacing.
  if (!response.ok() && response.status() !== 409 && response.status() !== 400) {
    throw new Error(`Seeding ${user.email} failed: ${response.status()} ${await response.text()}`);
  }
}

/**
 * Role is a plain column with no promotion endpoint, so this is the only way
 * to get an ADMIN. Safe here because the database is a tmpfs that dies with
 * the stack.
 */
function promoteToAdmin(email: string): void {
  try {
    execFileSync(
      'docker',
      [
        'compose', '-f', COMPOSE_FILE, 'exec', '-T', 'db',
        'psql', '-U', 'teacher', '-d', 'teacher_videos',
        '-v', 'ON_ERROR_STOP=1',
        '-c', `UPDATE users SET role = 'ADMIN' WHERE email = '${email}';`,
      ],
      { stdio: 'pipe' },
    );
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`Could not promote ${email} to ADMIN via docker compose exec.\n${detail}`);
  }
}
