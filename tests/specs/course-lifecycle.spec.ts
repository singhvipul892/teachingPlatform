import { authHeader, destructiveTest as test, expect } from '../src/fixtures';

/**
 * @destructive — ephemeral stack only.
 *
 * What retiring a course means, and what closing enrolment means. The rule that
 * ties both together: these gates are about *joining*. They never touch anyone
 * already enrolled, and they never block an admin from correcting the roster —
 * freezing a retired course's roster would mean putting it back on public sale
 * just to remove one student.
 */

const unique = () => `QA lifecycle ${Date.now()}-${Math.floor(Math.random() * 1000)}`;

/** Enrolment dates are Indian calendar days, so ask the clock in that zone. */
function istDatePlusDays(days: number): string {
  const parts = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Kolkata' });
  const [y, m, d] = parts.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

async function makeCourse(
  api: any,
  headers: Record<string, string>,
  extra: Record<string, string> = {},
) {
  const created = await api.post('/api/admin/courses', {
    headers,
    multipart: {
      title: unique(),
      description: 'created by the lifecycle suite',
      pricePaise: '50000',
      currency: 'INR',
      active: 'true',
      ...extra,
    },
  });
  expect(created.status()).toBe(201);
  return created.json();
}

const setActive = (api: any, headers: Record<string, string>, courseId: number, active: boolean) =>
  api.patch(`/api/admin/courses/${courseId}`, { headers, data: { active } });

test.describe('@destructive taking a course off sale', () => {
  test('an enrolled student keeps everything when the course is retired', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers);
    await api.post(`/api/admin/courses/${course.id}/students`, { headers, data: { userId: student.userId } });

    expect((await setActive(api, headers, course.id, false)).status()).toBe(200);

    // Off sale is not revocation: this is the rule the admin panel now states
    // out loud, because the old wording claimed the opposite.
    expect(
      (await api.get(`/api/courses/${course.id}/videos`, { headers: authHeader(student) })).status(),
      'a retired course keeps serving the students who bought it',
    ).toBe(200);

    const mine = await api.get('/api/user/courses', { headers: authHeader(student) });
    const listed = (await mine.json()).purchasedCourses.find((c: { id: number }) => c.id === course.id);
    expect(listed, 'and it stays in their list').toBeDefined();
    expect(listed.expired).toBe(false);
  });

  test('a retired course cannot be bought, and says it cannot be renewed', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers);
    await api.post(`/api/admin/courses/${course.id}/students`, { headers, data: { userId: student.userId } });
    await setActive(api, headers, course.id, false);

    const catalogue = await (await api.get('/api/courses')).json();
    expect(catalogue.map((c: { id: number }) => c.id), 'gone from the catalogue').not.toContain(course.id);

    // The dead end this closes: the app told expired students to "purchase it
    // again on the website", where a retired course cannot be bought at all.
    const mine = await api.get('/api/user/courses', { headers: authHeader(student) });
    const listed = (await mine.json()).purchasedCourses.find((c: { id: number }) => c.id === course.id);
    expect(listed.renewable, 'so the client can say so instead of offering Buy Again').toBe(false);
  });

  test('an admin can still fix the roster of a retired course', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers);
    const studentsPath = `/api/admin/courses/${course.id}/students`;
    await api.post(studentsPath, { headers, data: { userId: student.userId } });
    await setActive(api, headers, course.id, false);

    // Every corrective action stays open. Blocking these would mean putting a
    // course back on public sale just to remove one student.
    expect(
      (await api.put(`${studentsPath}/${student.userId}/expiry`, { headers, data: { expiryDate: null } })).status(),
      'expiry edits',
    ).toBe(200);
    expect((await api.delete(`${studentsPath}/${student.userId}`, { headers })).status(), 'untag').toBe(204);
    expect(
      (await api.post(`${studentsPath}/${student.userId}/restore`, { headers })).status(),
      'restore',
    ).toBe(200);
  });

  test('a payment taken before retirement can still be recorded', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers);
    await setActive(api, headers, course.id, false);

    // Warned in the panel, never blocked by the API: refusing would strand a
    // real payment whose only sin is late paperwork.
    const tagged = await api.post(`/api/admin/courses/${course.id}/students`, {
      headers,
      data: { userId: student.userId },
    });
    expect(tagged.status(), await tagged.text()).toBe(201);
  });
});

test.describe('@destructive closing enrolment on a date', () => {
  test('a course with an open window behaves normally', async ({ api, admin }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers, { enrolmentClosesOn: istDatePlusDays(30) });

    expect(course.enrolmentClosesOn).toBe(istDatePlusDays(30));
    expect(course.enrolmentOpen).toBe(true);
    // The two dates are different things and must not be conflated.
    expect(course.validityDays, 'closing enrolment is not access length').toBe(0);
  });

  test('a closed window stops new students buying', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const closed = istDatePlusDays(-1);
    const course = await makeCourse(api, headers, { enrolmentClosesOn: closed });

    const read = await (await api.get(`/api/admin/courses/${course.id}`, { headers })).json();
    expect(read.enrolmentOpen).toBe(false);

    const buy = await api.post('/api/payment/create-order', {
      headers: authHeader(student),
      data: { courseId: course.id },
    });
    expect(buy.status()).toBe(409);
    expect(await buy.text()).toContain(closed);
  });

  test('closing enrolment leaves the students already in it alone', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers);
    await api.post(`/api/admin/courses/${course.id}/students`, { headers, data: { userId: student.userId } });

    const patched = await api.patch(`/api/admin/courses/${course.id}`, {
      headers,
      data: { enrolmentClosesOn: istDatePlusDays(-1) },
    });
    expect(patched.status()).toBe(200);
    expect((await patched.json()).enrolmentOpen).toBe(false);

    expect(
      (await api.get(`/api/courses/${course.id}/videos`, { headers: authHeader(student) })).status(),
      'the door closed behind them, not on them',
    ).toBe(200);
  });

  test('an admin can still tag after the window closes', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers, { enrolmentClosesOn: istDatePlusDays(-1) });

    const tagged = await api.post(`/api/admin/courses/${course.id}/students`, {
      headers,
      data: { userId: student.userId },
    });
    expect(tagged.status(), 'same reasoning as a retired course').toBe(201);
  });
});

test.describe('@destructive one payment reference, several courses', () => {
  test('the same reference can cover two courses', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const first = await makeCourse(api, headers);
    const second = await makeCourse(api, headers);
    const reference = `UPI-BUNDLE-${Date.now()}`;

    // One bank transfer paying for two courses. This used to fail with a
    // duplicate-key 500 on the second one.
    for (const course of [first, second]) {
      const tagged = await api.post(`/api/admin/courses/${course.id}/students`, {
        headers,
        data: { userId: student.userId, razorpayTransactionId: reference },
      });
      expect(tagged.status(), await tagged.text()).toBe(201);
    }

    for (const course of [first, second]) {
      const roster = await api.get(`/api/admin/courses/${course.id}/students`, { headers });
      expect((await roster.json()).map((s: { id: number }) => s.id)).toContain(student.userId);
    }
  });

  test('the same reference twice on one course is still refused', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers);
    const studentsPath = `/api/admin/courses/${course.id}/students`;
    const reference = `UPI-DUP-${Date.now()}`;

    expect(
      (await api.post(studentsPath, { headers, data: { userId: student.userId, razorpayTransactionId: reference } })).status(),
    ).toBe(201);

    await api.delete(`${studentsPath}/${student.userId}`, { headers });

    // Same student, same course, same reference: that is one payment being
    // entered twice, and it must not become two.
    const duplicate = await api.post(studentsPath, {
      headers,
      data: { userId: student.userId, razorpayTransactionId: reference, recordPayment: true },
    });
    expect(duplicate.status()).not.toBe(201);
  });
});
