import type { APIRequestContext } from '@playwright/test';

/**
 * Read-only enforcement for the production target.
 *
 * The project-level `grep: /@readonly/` already keeps destructive specs out of
 * the prod run. This is the backstop underneath it: a mistyped `post()` inside
 * a spec that *is* tagged @readonly would otherwise sail straight into the only
 * environment that exists. Here it throws before the request leaves the machine.
 */
export class ReadOnlyViolation extends Error {
  constructor(method: string, url: string) {
    super(
      `Blocked ${method} ${url}.\n` +
        'This run targets production, where the test suite is read-only.\n' +
        'The backend cannot delete users or hard-delete courses, so anything written ' +
        'there is permanent. Move this assertion to a @destructive spec and run it ' +
        'against the ephemeral stack (npm run stack:up).',
    );
    this.name = 'ReadOnlyViolation';
  }
}

/**
 * The single exception to "GET only".
 *
 * POST /api/auth/login is a pure read: it verifies a bcrypt hash and mints a
 * JWT without touching a row. Without it there is no token, and without a token
 * the 401-vs-403 authorization matrix — the most valuable thing the prod suite
 * checks — cannot run at all.
 *
 * Nothing else belongs here. /api/auth/signup creates a permanently
 * undeletable user, and /api/auth/forgot-password sends a real SMS through AWS
 * SNS to whoever owns that number.
 */
const SAFE_POST_PATHS = ['/api/auth/login'];

const MUTATING_METHODS = ['post', 'put', 'patch', 'delete'] as const;

function pathOf(url: string): string {
  try {
    return new URL(url, 'http://placeholder.invalid').pathname;
  } catch {
    return url;
  }
}

function isSafePost(method: string, url: string): boolean {
  return method.toUpperCase() === 'POST' && SAFE_POST_PATHS.includes(pathOf(url));
}

/**
 * Wraps an APIRequestContext so mutating calls throw. Returns the context
 * untouched when the target is the ephemeral stack, where writing is the point.
 */
export function enforceReadOnly(ctx: APIRequestContext, readOnly: boolean): APIRequestContext {
  if (!readOnly) return ctx;

  return new Proxy(ctx, {
    get(target, prop, receiver) {
      if (typeof prop === 'string' && (MUTATING_METHODS as readonly string[]).includes(prop)) {
        return (url: string, ...rest: unknown[]) => {
          if (isSafePost(prop, url)) {
            return (target[prop as 'post'] as Function).call(target, url, ...rest);
          }
          throw new ReadOnlyViolation(prop.toUpperCase(), url);
        };
      }

      // fetch() carries its method in the options bag, so it needs its own check.
      if (prop === 'fetch') {
        return (url: string, options: { method?: string } = {}) => {
          const method = (options.method ?? 'GET').toUpperCase();
          if (method !== 'GET' && method !== 'HEAD' && !isSafePost(method, url)) {
            throw new ReadOnlyViolation(method, url);
          }
          return target.fetch(url, options as Parameters<APIRequestContext['fetch']>[1]);
        };
      }

      const value = Reflect.get(target, prop, receiver);
      return typeof value === 'function' ? value.bind(target) : value;
    },
  });
}

/**
 * Hard stop for destructive specs. Belt to the project-grep's braces: if a
 * @destructive spec is ever run with a non-loopback baseURL, it fails loudly
 * instead of quietly writing to production.
 */
export function assertEphemeral(baseURL: string | undefined, ephemeral: boolean): void {
  if (!ephemeral) {
    throw new Error(
      `Refusing to run a @destructive spec against ${baseURL ?? '(no baseURL)'}.\n` +
        'Destructive tests only run against the loopback stack on 127.0.0.1.\n' +
        'Start it with: npm run stack:up',
    );
  }
}
