import { authHeader, destructiveTest as test, expect } from '../src/fixtures';

/**
 * @destructive — ephemeral stack only.
 *
 * Only the paths that reject. The happy path is deliberately not tested: it
 * would create a real order in the Razorpay dashboard and a purchase row that
 * nothing can remove. What matters here is that forged payments are refused,
 * and that is entirely local HMAC arithmetic.
 */
test.describe('@destructive payment rejects what it should', () => {
  test('a forged signature does not unlock the course', async ({ api, admin }) => {
    const response = await api.post('/api/payment/verify', {
      headers: authHeader(admin),
      data: {
        razorpayOrderId: 'order_forged_by_test',
        razorpayPaymentId: 'pay_forged_by_test',
        razorpaySignature: 'this-is-not-a-valid-hmac',
      },
    });

    // The exact status matters less than the outcome: this must never come
    // back as a successful verification.
    if (response.status() === 200) {
      expect((await response.json()).success, 'a forged signature was accepted').toBe(false);
    } else {
      expect(response.status()).toBeGreaterThanOrEqual(400);
    }
  });

  test('ordering a course that does not exist is rejected before Razorpay is called', async ({ api, admin }) => {
    const response = await api.post('/api/payment/create-order', {
      headers: authHeader(admin),
      data: { courseId: 999999 },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
    expect(response.status()).toBeLessThan(500);
  });

  test('creating an order without a token is 401', async ({ api }) => {
    const response = await api.post('/api/payment/create-order', { data: { courseId: 1 } });
    expect(response.status()).toBe(401);
  });

  test('verifying without a token is 401', async ({ api }) => {
    const response = await api.post('/api/payment/verify', {
      data: { razorpayOrderId: 'x', razorpayPaymentId: 'y', razorpaySignature: 'z' },
    });
    expect(response.status()).toBe(401);
  });

  test('a fresh account owns nothing', async ({ api, admin }) => {
    const response = await api.get('/api/payment/status', { headers: authHeader(admin) });
    expect(response.status()).toBe(200);
    expect((await response.json()).hasActivePurchase).toBe(false);
  });
});
