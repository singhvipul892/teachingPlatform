import { destructiveTest as test, expect } from '../src/fixtures';

/**
 * @destructive — ephemeral stack only.
 *
 * forgot-password emails a real OTP in production. Here MAIL_MOCK is true, so
 * the code only reaches the api container's log and these specs cannot read
 * it: they cover everything short of a successful reset — enumeration safety,
 * validation, and the wrong-guess lockout.
 */
test.describe('@destructive password reset', () => {
  async function freshUser(api: import('@playwright/test').APIRequestContext) {
    const stamp = String(Date.now());
    const user = {
      firstName: 'QA',
      lastName: 'Reset',
      email: `qa-reset-${stamp}@ephemeral.invalid`,
      mobileNumber: `7${stamp.slice(-9)}`,
      password: 'QaReset!2345',
    };
    expect((await api.post('/api/auth/signup', { data: user })).status()).toBe(200);
    return user;
  }

  test('an unknown email gets the same 200 as a real one', async ({ api }) => {
    const response = await api.post('/api/auth/forgot-password', {
      data: { email: `nobody-${Date.now()}@ephemeral.invalid` },
    });
    expect(response.status()).toBe(200);
  });

  test('a malformed email is rejected', async ({ api }) => {
    const response = await api.post('/api/auth/forgot-password', { data: { email: 'not-an-email' } });
    expect(response.status()).toBe(400);
  });

  test('reset without requesting an OTP first is refused', async ({ api }) => {
    const user = await freshUser(api);
    const response = await api.post('/api/auth/reset-password', {
      data: { email: user.email, otp: '000000', newPassword: 'Changed!2345' },
    });
    expect(response.status()).toBe(400);
    expect((await response.json()).message).toMatch(/No active OTP/);
  });

  test('five wrong OTPs burn the code and the password is untouched', async ({ api }) => {
    const user = await freshUser(api);
    expect((await api.post('/api/auth/forgot-password', { data: { email: user.email } })).status()).toBe(200);

    const attempt = () =>
      api.post('/api/auth/reset-password', {
        data: { email: user.email, otp: '000000', newPassword: 'Changed!2345' },
      });

    for (let i = 0; i < 4; i++) {
      const wrong = await attempt();
      expect(wrong.status()).toBe(400);
      expect((await wrong.json()).message).toBe('Invalid OTP.');
    }
    const fifth = await attempt();
    expect((await fifth.json()).message).toMatch(/Too many wrong attempts/);

    // Burned: even the right code would now find no active OTP.
    expect((await (await attempt()).json()).message).toMatch(/No active OTP/);

    const login = await api.post('/api/auth/login', {
      data: { username: user.email, password: user.password },
    });
    expect(login.status()).toBe(200);
  });
});
