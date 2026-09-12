import { expect, test } from '../src/fixtures';
import { requireQaUser } from '../src/config';
import { EPHEMERAL_STUDENT } from '../src/config';

/**
 * Login only. It is the one POST allowed against production, because it
 * verifies a hash and mints a JWT without writing a row — see the allowlist in
 * src/guards.ts.
 *
 * Signup, forgot-password and reset-password are deliberately absent: signup
 * creates a user the API has no way to delete, and forgot-password sends a
 * real SMS through AWS SNS.
 */
test.describe('@readonly login', () => {
  function credentials(ephemeral: boolean) {
    return ephemeral
      ? { username: EPHEMERAL_STUDENT.email, password: EPHEMERAL_STUDENT.password }
      : requireQaUser();
  }

  test('correct credentials return a usable token', async ({ api, ephemeral }) => {
    const { username, password } = credentials(ephemeral);
    const response = await api.post('/api/auth/login', { data: { username, password } });

    expect(response.status()).toBe(200);
    const body = await response.json();

    expect(typeof body.token).toBe('string');
    expect(body.token.split('.')).toHaveLength(3); // header.payload.signature
    expect(typeof body.userId).toBe('number');
    expect(body.role).toBe('USER');
  });

  test('the login response never contains the password hash', async ({ api, ephemeral }) => {
    const { username, password } = credentials(ephemeral);
    const response = await api.post('/api/auth/login', { data: { username, password } });

    const raw = await response.text();
    expect(raw).not.toContain('passwordHash');
    expect(raw).not.toContain('$2a$'); // bcrypt prefix
  });

  test('a wrong password is 401', async ({ api, ephemeral }) => {
    const { username } = credentials(ephemeral);
    const response = await api.post('/api/auth/login', {
      data: { username, password: 'definitely-not-the-password' },
    });
    expect(response.status()).toBe(401);
  });

  test('an unknown account is 401', async ({ api }) => {
    const response = await api.post('/api/auth/login', {
      data: { username: 'nobody-here@ephemeral.invalid', password: 'whatever12345' },
    });
    expect(response.status()).toBe(401);
  });

  test('a blank identifier is rejected, not treated as a match', async ({ api }) => {
    const response = await api.post('/api/auth/login', { data: { username: '', password: 'x' } });
    expect([400, 401]).toContain(response.status());
  });

  test('login works by mobile number as well as email', async ({ api, ephemeral }) => {
    test.skip(!ephemeral, 'needs the QA account mobile number, which prod config does not carry');

    const response = await api.post('/api/auth/login', {
      data: { username: EPHEMERAL_STUDENT.mobileNumber, password: EPHEMERAL_STUDENT.password },
    });
    expect(response.status()).toBe(200);
  });
});
