import { expect, test } from '../src/fixtures';

/**
 * The post-deploy check. Every assertion here is a GET, so this is safe to run
 * against production as often as you like — it replaces the curl in
 * .github/workflows/deploy.yml, which passed even when the API was dead
 * (nginx answered 301 and curl without -L called that success).
 */
test.describe('@readonly deploy smoke', () => {
  test('the API is actually serving, not just nginx', async ({ api }) => {
    const response = await api.get('/api/courses');

    // The specific failure the old curl check could not see: nginx up, API down.
    expect(
      response.status(),
      'GET /api/courses did not return 200 — nginx may be up while the API is down',
    ).toBe(200);
    expect(Array.isArray(await response.json())).toBe(true);
  });

  test('the database is reachable behind the API', async ({ api }) => {
    // /api/courses hits Postgres. A 5xx here is usually the DB, not the API.
    const response = await api.get('/api/courses');
    expect(response.status()).toBeLessThan(500);
  });

  test('the student login page is served', async ({ api }) => {
    const response = await api.get('/web/auth/login.html');
    expect(response.status()).toBe(200);
    expect(await response.text()).toContain('<html');
  });

  test('an unknown API route is refused, not 5xx-ed', async ({ api }) => {
    const response = await api.get('/api/definitely-not-a-real-route');

    // 401, not 404: everything under /api that is not explicitly permitted
    // requires authentication, and the security filter runs before routing.
    // That is the correct behaviour — it also means the API does not leak
    // which routes exist to anonymous callers.
    expect(response.status()).toBe(401);
  });

  /**
   * KNOWN GAP — expected to fail today. Delete the `test.fail()` once certbot
   * has run and PROD_BASE_URL is switched to https.
   *
   * Port 443 refuses connections: nginx.conf carries a complete SSL server
   * block, but the certificate was never issued, so the server is still
   * running nginx.no-ssl.conf. Until then every login, JWT and Razorpay
   * checkout on the site crosses the internet in cleartext.
   */
  test('the site is reachable over HTTPS', async ({ request, ephemeral }) => {
    test.skip(ephemeral, 'the throwaway stack is plain HTTP by design');
    test.fail();

    const response = await request.get('https://teacherplatform.duckdns.org/api/courses', {
      timeout: 10_000,
    });
    expect(response.status()).toBe(200);
  });

  test('auth is wired up — a protected route rejects anonymous callers', async ({ api }) => {
    // If this ever returns 200, the security filter chain is not applied and
    // every student's data is public. Worth failing a deploy over.
    const response = await api.get('/api/user/courses');
    expect(response.status()).toBe(401);
  });
});
