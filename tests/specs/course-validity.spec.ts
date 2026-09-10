import { authHeader, destructiveTest as test, expect } from '../src/fixtures';

/**
 * @destructive — ephemeral stack only.
 *
 * Course validity: an optional expiry stamped onto a purchase at the moment it
 * is made. The rule worth protecting here is that the stamp is a snapshot —
 * editing a course afterwards must never move a student who already bought.
 *
 * These enrol students through the admin tag endpoint rather than Razorpay, so
 * no real payment is involved; the expiry is stamped by the same code either
 * way (AdminCourseService.tagStudent / PaymentService.verifyPayment).
 */

/** Course expiry is an Indian-time calendar day, so ask the clock in that zone. */
function istToday(): Date {
  const parts = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Kolkata' });
  const [y, m, d] = parts.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

function istDatePlusDays(days: number): string {
  const date = istToday();
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

const unique = () => `QA validity ${Date.now()}-${Math.floor(Math.random() * 1000)}`;

async function createCourse(
  api: any,
  headers: Record<string, string>,
  validityDays: number,
): Promise<{ id: number; validityDays: number }> {
  const created = await api.post('/api/admin/courses', {
    headers,
    multipart: {
      title: unique(),
      description: 'created by the validity suite',
      pricePaise: '10000',
      currency: 'INR',
      active: 'true',
      validityDays: String(validityDays),
    },
  });
  expect(created.status()).toBe(201);
  return created.json();
}

async function enrol(api: any, headers: Record<string, string>, courseId: number, userId: number) {
  const tagged = await api.post(`/api/admin/courses/${courseId}/students`, {
    headers,
    data: { userId },
  });
  expect(tagged.status()).toBe(201);
  return tagged.json();
}

async function purchasedCourse(api: any, student: { token: string }, courseId: number) {
  const response = await api.get('/api/user/courses', { headers: authHeader(student) });
  expect(response.status()).toBe(200);
  const body = await response.json();
  return body.purchasedCourses.find((c: { id: number }) => c.id === courseId);
}

test.describe('@destructive course validity', () => {
  test('validity 0 means access never expires', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await createCourse(api, headers, 0);
    expect(course.validityDays).toBe(0);

    await enrol(api, headers, course.id, student.userId);

    const mine = await purchasedCourse(api, student, course.id);
    expect(mine.expiryDate).toBeNull();
    expect(mine.expired).toBe(false);
    expect(mine.daysRemaining).toBeNull();

    // And the course is genuinely usable.
    const videos = await api.get(`/api/courses/${course.id}/videos`, { headers: authHeader(student) });
    expect(videos.status()).toBe(200);
  });

  test('a validity is stamped onto the purchase as a dated expiry', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await createCourse(api, headers, 30);

    await enrol(api, headers, course.id, student.userId);

    const mine = await purchasedCourse(api, student, course.id);
    expect(mine.expiryDate).toBe(istDatePlusDays(30));
    expect(mine.expired).toBe(false);
    expect(mine.daysRemaining).toBe(30);
  });

  test('editing a course validity does not move a student who already bought', async ({
    api,
    admin,
    student,
  }) => {
    const headers = authHeader(admin);
    const course = await createCourse(api, headers, 30);
    await enrol(api, headers, course.id, student.userId);

    const before = await purchasedCourse(api, student, course.id);

    // Shorten it drastically, then remove it entirely.
    for (const validityDays of [1, 0]) {
      const patched = await api.patch(`/api/admin/courses/${course.id}`, {
        headers,
        data: { validityDays },
      });
      expect(patched.status()).toBe(200);
      expect((await patched.json()).validityDays).toBe(validityDays);

      const after = await purchasedCourse(api, student, course.id);
      expect(
        after.expiryDate,
        'an existing student kept the expiry they were sold',
      ).toBe(before.expiryDate);
    }
  });

  test('an expired student loses the videos and can buy again', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await createCourse(api, headers, 30);
    await enrol(api, headers, course.id, student.userId);

    // While access is live, buying again is refused outright — before Razorpay
    // is ever contacted, which is what makes this safe to assert here.
    const whileActive = await api.post('/api/payment/create-order', {
      headers: authHeader(student),
      data: { courseId: course.id },
    });
    expect(whileActive.status()).toBe(409);

    // Backdate the expiry to yesterday.
    const expiredOn = istDatePlusDays(-1);
    const overridden = await api.put(`/api/admin/courses/${course.id}/students/${student.userId}/expiry`, {
      headers,
      data: { expiryDate: expiredOn },
    });
    expect(overridden.status()).toBe(200);
    expect((await overridden.json()).expired).toBe(true);

    // The course still appears in the student's list, flagged as expired...
    const mine = await purchasedCourse(api, student, course.id);
    expect(mine).toBeDefined();
    expect(mine.expired).toBe(true);
    expect(mine.expiryDate).toBe(expiredOn);
    expect(mine.daysRemaining).toBe(-1);

    // ...but the content is gone.
    const videos = await api.get(`/api/courses/${course.id}/videos`, { headers: authHeader(student) });
    expect(videos.status()).toBe(403);

    // And buying is no longer blocked. It gets past the conflict check into
    // Razorpay, which the ephemeral stack has no working credentials for — any
    // answer but 409 proves the gate opened.
    const whileExpired = await api.post('/api/payment/create-order', {
      headers: authHeader(student),
      data: { courseId: course.id },
    });
    expect(whileExpired.status()).not.toBe(409);
  });

  test('an admin can restore access, up to making it lifetime', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await createCourse(api, headers, 30);
    await enrol(api, headers, course.id, student.userId);

    const expirePath = `/api/admin/courses/${course.id}/students/${student.userId}/expiry`;

    await api.put(expirePath, { headers, data: { expiryDate: istDatePlusDays(-1) } });
    expect((await api.get(`/api/courses/${course.id}/videos`, { headers: authHeader(student) })).status()).toBe(403);

    // Push it forward again.
    const extended = istDatePlusDays(90);
    const pushed = await api.put(expirePath, { headers, data: { expiryDate: extended } });
    expect(pushed.status()).toBe(200);
    expect((await pushed.json()).expiryDate).toBe(extended);
    expect((await api.get(`/api/courses/${course.id}/videos`, { headers: authHeader(student) })).status()).toBe(200);

    // A null date means lifetime.
    const lifetime = await api.put(expirePath, { headers, data: { expiryDate: null } });
    expect(lifetime.status()).toBe(200);
    expect((await lifetime.json()).expiryDate).toBeNull();
    expect((await lifetime.json()).expired).toBe(false);
  });

  test('setting an expiry for a student who is not enrolled is 404', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await createCourse(api, headers, 30);

    const response = await api.put(`/api/admin/courses/${course.id}/students/${student.userId}/expiry`, {
      headers,
      data: { expiryDate: istDatePlusDays(10) },
    });
    expect(response.status()).toBe(404);
  });

  test('the admin course list separates active enrolments from total', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await createCourse(api, headers, 30);
    await enrol(api, headers, course.id, student.userId);

    const live = await (await api.get(`/api/admin/courses/${course.id}`, { headers })).json();
    expect(live.studentCount).toBe(1);
    expect(live.activeStudentCount).toBe(1);

    await api.put(`/api/admin/courses/${course.id}/students/${student.userId}/expiry`, {
      headers,
      data: { expiryDate: istDatePlusDays(-1) },
    });

    const lapsed = await (await api.get(`/api/admin/courses/${course.id}`, { headers })).json();
    expect(lapsed.studentCount, 'the sale still happened').toBe(1);
    expect(lapsed.activeStudentCount, 'but nobody can use it').toBe(0);
  });
});
