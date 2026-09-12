import * as path from 'node:path';
import * as dotenv from 'dotenv';

dotenv.config({ path: path.resolve(__dirname, '..', '.env') });

/** The throwaway stack from docker-compose.test.yml. Destructive tests only ever run here. */
export const EPHEMERAL_BASE_URL = process.env.TEST_BASE_URL ?? 'http://127.0.0.1:18080';

/**
 * The single real environment. Read-only, always.
 *
 * http, not https: port 443 currently refuses connections. nginx.conf has the
 * SSL server block ready, but certbot has never been run on the server, so it
 * is still serving nginx.no-ssl.conf. Change this to https the day that is
 * fixed — smoke.spec.ts has a test that flips to an unexpected pass when it is.
 */
export const PROD_BASE_URL = process.env.PROD_BASE_URL ?? 'http://teacherplatform.duckdns.org';

/**
 * The one and only decision that separates "may write" from "must not write".
 *
 * Deliberately keyed off the hostname rather than an env flag, because an env
 * flag is one typo away from pointing destructive tests at production. There is
 * no override, and adding one would defeat the entire design — see README.md.
 */
export function isEphemeral(baseURL: string | undefined): boolean {
  if (!baseURL) return false;
  let hostname: string;
  try {
    hostname = new URL(baseURL).hostname;
  } catch {
    return false;
  }
  return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '[::1]' || hostname === '::1';
}

/**
 * The permanent QA account. Created by hand once, never deleted, never buys
 * anything. Logging in as it writes nothing to the database, which is what
 * makes the authorization-matrix tests safe to run against production.
 */
export const QA_USER = {
  username: process.env.QA_USER_USERNAME ?? '',
  password: process.env.QA_USER_PASSWORD ?? '',
};

export function requireQaUser(): { username: string; password: string } {
  if (!QA_USER.username || !QA_USER.password) {
    throw new Error(
      'QA_USER_USERNAME and QA_USER_PASSWORD must be set (tests/.env, or CI secrets).\n' +
        'See tests/README.md -> "One-time production setup".',
    );
  }
  return QA_USER;
}

/** Credentials the ephemeral stack seeds for itself. Meaningless anywhere else. */
export const EPHEMERAL_ADMIN = {
  firstName: 'QA',
  lastName: 'Admin',
  email: 'qa-admin@ephemeral.invalid',
  mobileNumber: '9000000001',
  password: 'EphemeralAdmin!234',
};

export const EPHEMERAL_STUDENT = {
  firstName: 'QA',
  lastName: 'Student',
  email: 'qa-student@ephemeral.invalid',
  mobileNumber: '9000000002',
  password: 'EphemeralStudent!234',
};
