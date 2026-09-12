import { expect, test } from '../src/fixtures';
import { ReadOnlyViolation, enforceReadOnly } from '../src/guards';

/**
 * Tests the safety net, not the backend.
 *
 * The whole design rests on one claim: a write aimed at production is stopped
 * before it leaves this machine. An untested guard is just a comment, so these
 * assertions run in every prod suite alongside the real checks.
 */
test.describe('@readonly the read-only guard', () => {
  test('blocks writes when the target is production', async ({ api, ephemeral, baseURL }) => {
    test.skip(ephemeral, 'the guard is intentionally inert against the throwaway stack');

    expect(
      baseURL,
      'a non-loopback baseURL must put the suite in read-only mode',
    ).not.toMatch(/127\.0\.0\.1|localhost/);

    // Each of these would be unrecoverable against the only real environment.
    // The guard throws synchronously, at the call site, before any request is
    // built — hence toThrow() rather than rejects.toThrow().
    expect(() => api.post('/api/auth/signup', { data: {} })).toThrow(ReadOnlyViolation);
    expect(() => api.post('/api/admin/courses', { multipart: {} })).toThrow(ReadOnlyViolation);
    expect(() => api.delete('/api/admin/courses/1')).toThrow(ReadOnlyViolation);
    expect(() => api.patch('/api/admin/courses/1', { data: {} })).toThrow(ReadOnlyViolation);
    expect(() => api.put('/api/admin/courses/1', { multipart: {} })).toThrow(ReadOnlyViolation);
  });

  test('blocks forgot-password, which would send a real SMS', async ({ api, ephemeral }) => {
    test.skip(ephemeral, 'SMS_MOCK is true on the throwaway stack');

    // Prod runs with SMS_MOCK=false, so this reaches AWS SNS and texts whoever
    // owns that number. It is not on the allowlist and must never be.
    expect(() =>
      api.post('/api/auth/forgot-password', { data: { mobileNumber: '9999999999' } }),
    ).toThrow(ReadOnlyViolation);
  });

  test('blocks writes smuggled through fetch()', async ({ api, ephemeral }) => {
    test.skip(ephemeral, 'writes are the point against the throwaway stack');

    // fetch() carries its method in the options bag rather than the method
    // name, so it needs its own branch in the guard.
    expect(() => api.fetch('/api/admin/courses', { method: 'DELETE' })).toThrow(ReadOnlyViolation);
  });

  test('still allows login, the one permitted write-shaped call', async ({ api, ephemeral }) => {
    test.skip(ephemeral, 'covered by the prod run');

    // Wrong credentials on purpose — this asserts the call is permitted
    // through the guard, not that it succeeds.
    const response = await api.post('/api/auth/login', {
      data: { username: 'guard-probe@ephemeral.invalid', password: 'wrong-on-purpose' },
    });
    expect(response.status()).toBe(401);
  });

  test('the allowlist is exactly one path', () => {
    // A unit check, so that widening the allowlist is a deliberate act that
    // breaks a test rather than a quiet one-line edit.
    const fakeContext = {
      post: async () => 'allowed',
      fetch: async () => 'allowed',
    } as never;
    const guarded = enforceReadOnly(fakeContext, true);

    expect(() => guarded.post('/api/auth/signup')).toThrow(ReadOnlyViolation);
    expect(() => guarded.post('/api/payment/verify')).toThrow(ReadOnlyViolation);
    expect(() => guarded.post('/api/auth/login')).not.toThrow();
    // Query strings and absolute URLs must resolve to the same path.
    expect(() => guarded.post('https://example.invalid/api/auth/login?x=1')).not.toThrow();
  });
});
