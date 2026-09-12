import { destructiveTest as test, expect } from '../src/fixtures';

/**
 * @destructive — ephemeral stack only.
 *
 * Signup is permanently off-limits against production: there is no
 * user-delete endpoint anywhere in the API (AdminUserController exposes only
 * POST and GET), and a user with any payment_orders row cannot be removed by
 * hand either, because that foreign key has no ON DELETE clause. Every account
 * created here would be forever.
 */
test.describe('@destructive signup', () => {
  const newUser = (suffix: string) => ({
    firstName: 'QA',
    lastName: 'Signup',
    email: `qa-signup-${suffix}@ephemeral.invalid`,
    mobileNumber: `9${String(Date.now()).slice(-9)}`,
    password: 'QaSignup!2345',
  });

  test('a new account is created and can immediately log in', async ({ api }) => {
    const user = newUser(String(Date.now()));

    const created = await api.post('/api/auth/signup', { data: user });
    expect(created.status()).toBe(200);

    const login = await api.post('/api/auth/login', {
      data: { username: user.email, password: user.password },
    });
    expect(login.status()).toBe(200);

    const body = await login.json();
    expect(body.email).toBe(user.email);
    // New accounts must never arrive with elevated privileges.
    expect(body.role).toBe('USER');
  });

  test('a duplicate email is 409, not a second account', async ({ api }) => {
    const user = newUser(`dupe-${Date.now()}`);
    expect((await api.post('/api/auth/signup', { data: user })).status()).toBe(200);

    const second = await api.post('/api/auth/signup', {
      data: { ...user, mobileNumber: `8${String(Date.now()).slice(-9)}` },
    });
    expect(second.status()).toBe(409);
  });

  test('a duplicate mobile number is 409', async ({ api }) => {
    const user = newUser(`mob-${Date.now()}`);
    expect((await api.post('/api/auth/signup', { data: user })).status()).toBe(200);

    const second = await api.post('/api/auth/signup', {
      data: { ...user, email: `qa-signup-other-${Date.now()}@ephemeral.invalid` },
    });
    expect(second.status()).toBe(409);
  });

  test('a short password is rejected', async ({ api }) => {
    const response = await api.post('/api/auth/signup', {
      data: { ...newUser(`short-${Date.now()}`), password: 'abc' },
    });
    expect(response.status()).toBe(400);
  });

  test('a malformed email is rejected', async ({ api }) => {
    const response = await api.post('/api/auth/signup', {
      data: { ...newUser(`bad-${Date.now()}`), email: 'not-an-email' },
    });
    expect(response.status()).toBe(400);
  });
});
