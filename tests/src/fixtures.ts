import { test as base, expect, request as playwrightRequest } from '@playwright/test';
import type { APIRequestContext } from '@playwright/test';
import { EPHEMERAL_ADMIN, EPHEMERAL_STUDENT, isEphemeral, requireQaUser } from './config';
import { assertEphemeral, enforceReadOnly } from './guards';

export interface AuthedUser {
  token: string;
  userId: number;
  role: string;
}

interface Fixtures {
  /** Request context for the target under test. Read-only unless the target is loopback. */
  api: APIRequestContext;
  /** True when the target is the throwaway stack and writes are allowed. */
  ephemeral: boolean;
  /** Token for the permanent QA account (prod) or the seeded student (ephemeral). */
  student: AuthedUser;
  /** Ephemeral-only. Requesting this on any other target fails the test. */
  admin: AuthedUser;
}

export async function login(
  api: APIRequestContext,
  username: string,
  password: string,
): Promise<AuthedUser> {
  const response = await api.post('/api/auth/login', { data: { username, password } });
  if (!response.ok()) {
    throw new Error(`Login failed for "${username}": ${response.status()} ${await response.text()}`);
  }
  const body = await response.json();
  return { token: body.token, userId: body.userId, role: body.role };
}

export function authHeader(user: AuthedUser): Record<string, string> {
  return { Authorization: `Bearer ${user.token}` };
}

export const test = base.extend<Fixtures>({
  ephemeral: async ({ baseURL }, use) => {
    await use(isEphemeral(baseURL));
  },

  api: async ({ baseURL, ephemeral }, use) => {
    const ctx = await playwrightRequest.newContext({
      baseURL,
      // Prod redirects HTTP->HTTPS; following it keeps specs from asserting on 301s.
      ignoreHTTPSErrors: false,
    });
    await use(enforceReadOnly(ctx, !ephemeral));
    await ctx.dispose();
  },

  student: async ({ api, ephemeral }, use) => {
    const creds = ephemeral
      ? { username: EPHEMERAL_STUDENT.email, password: EPHEMERAL_STUDENT.password }
      : requireQaUser();
    await use(await login(api, creds.username, creds.password));
  },

  admin: async ({ api, baseURL, ephemeral }, use) => {
    assertEphemeral(baseURL, ephemeral);
    await use(await login(api, EPHEMERAL_ADMIN.email, EPHEMERAL_ADMIN.password));
  },
});

/**
 * Use for any spec that writes. Fails immediately on a non-loopback target
 * rather than relying on the project grep alone.
 */
export const destructiveTest = test.extend<{ guardDestructive: void }>({
  guardDestructive: [
    async ({ baseURL, ephemeral }, use) => {
      assertEphemeral(baseURL, ephemeral);
      await use();
    },
    { auto: true },
  ],
});

export { expect };
