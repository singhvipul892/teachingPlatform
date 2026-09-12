import { authHeader, expect, test } from '../src/fixtures';

/**
 * The authorization matrix — the highest-value thing this suite checks, and
 * entirely read-only, so it runs against production unchanged.
 *
 * Every case distinguishes 401 (not logged in) from 403 (logged in, wrong
 * role). Collapsing those two is the classic way a paid platform springs a
 * leak, and it is invisible from the outside until someone notices.
 */
test.describe('@readonly anonymous callers are rejected', () => {
  const protectedGets = [
    '/api/user/courses',
    '/api/courses/1/videos',
    '/api/payment/status',
    '/api/admin/courses',
    '/api/admin/courses/1',
    '/api/admin/courses/1/students',
    '/api/admin/courses/1/videos',
    '/api/admin/users?q=test',
  ];

  for (const path of protectedGets) {
    test(`GET ${path} without a token is 401`, async ({ api }) => {
      const response = await api.get(path);
      expect(response.status()).toBe(401);
    });
  }

  test('a malformed token is 401, not a 500', async ({ api }) => {
    const response = await api.get('/api/user/courses', {
      headers: { Authorization: 'Bearer not-a-real-jwt' },
    });
    expect(response.status()).toBe(401);
  });

  test('a token signed with the wrong key is 401', async ({ api }) => {
    // Structurally valid JWT, signed with a key the server does not know.
    const forged =
      'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6IkFETUlOIiwiZXhwIjo0MTAyNDQ0ODAwfQ' +
      '.wrong-signature-value-that-will-not-verify';
    const response = await api.get('/api/admin/courses', {
      headers: { Authorization: `Bearer ${forged}` },
    });
    expect(response.status()).toBe(401);
  });
});

test.describe('@readonly a student token cannot reach admin endpoints', () => {
  const adminGets = [
    '/api/admin/courses',
    '/api/admin/courses/1',
    '/api/admin/courses/1/students',
    '/api/admin/courses/1/videos',
    '/api/admin/users?q=test',
  ];

  for (const path of adminGets) {
    test(`GET ${path} as a USER is 403`, async ({ api, student }) => {
      const response = await api.get(path, { headers: authHeader(student) });

      // 403, specifically. A 200 means role enforcement is gone; a 401 would
      // mean the token was rejected outright and the test proved nothing.
      expect(response.status()).toBe(403);
    });
  }

  test('the QA account is a plain USER, not an admin', async ({ student }) => {
    // Guards the suite itself: if this account ever gets promoted, every 403
    // assertion above silently stops testing anything.
    expect(student.role).toBe('USER');
  });
});

test.describe('@readonly a student token is accepted on student endpoints', () => {
  test('GET /api/user/courses returns the purchased list', async ({ api, student }) => {
    const response = await api.get('/api/user/courses', { headers: authHeader(student) });
    expect(response.status()).toBe(200);
  });

  test('GET /api/payment/status returns a boolean', async ({ api, student }) => {
    const response = await api.get('/api/payment/status', { headers: authHeader(student) });
    expect(response.status()).toBe(200);
    expect(typeof (await response.json()).hasActivePurchase).toBe('boolean');
  });

  test('a course the student has not bought does not expose its videos', async ({ api, student }) => {
    const response = await api.get('/api/courses/999999/videos', { headers: authHeader(student) });

    // Not 200. Either "no such course" or "not yours" — both are correct;
    // handing back the video list would not be.
    expect(response.status()).not.toBe(200);
  });
});

test.describe('@readonly PDF downloads are gated', () => {
  /**
   * This was a real hole, not a theoretical one: the endpoint took no auth
   * argument and made no purchase check, and SecurityConfig made
   * GET /api/videos/** public, so an anonymous caller with any valid pdf id was
   * handed a presigned S3 URL for paid material. The tell was the status code —
   * 404 from the service layer rather than 401 from the security filter, which
   * meant the request had reached business logic unauthenticated.
   *
   * Now it takes the same route as the video listing: resolve the user, then
   * CourseAccessGuard.requireAccess on the course the PDF's video belongs to.
   */
  test('an anonymous caller cannot get a presigned PDF URL', async ({ api }) => {
    const response = await api.get('/api/videos/1/pdfs/1/download');
    expect(response.status(), 'no token must be rejected before any lookup').toBe(401);
  });

  test('a signed-in student cannot download from a course they never bought', async ({ api, student }) => {
    const response = await api.get('/api/videos/1/pdfs/1/download', { headers: authHeader(student) });
    // 403 if that pdf exists, 404 if it does not — either way, never a URL.
    expect([403, 404]).toContain(response.status());
    expect(await response.text()).not.toContain('X-Amz-Signature');
  });
});
