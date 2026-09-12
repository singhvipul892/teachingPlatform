import { authHeader, destructiveTest as test, expect } from '../src/fixtures';

/**
 * @destructive — ephemeral stack only.
 *
 * Revenue is summed from payment_orders, not guessed from price × head count.
 * The rules worth protecting here are the ones the old estimate broke on
 * ordinary days: editing a price must not rewrite history, removing a student
 * must not erase a sale that happened, a second payment must count twice, and
 * a free seat must count nothing.
 */

const unique = () => `QA ledger ${Date.now()}-${Math.floor(Math.random() * 1000)}`;

async function makeCourse(api: any, headers: Record<string, string>, pricePaise: number) {
  const created = await api.post('/api/admin/courses', {
    headers,
    multipart: {
      title: unique(),
      description: 'created by the ledger suite',
      pricePaise: String(pricePaise),
      currency: 'INR',
      active: 'true',
    },
  });
  expect(created.status()).toBe(201);
  return created.json();
}

/** Revenue attributable to one course, from the admin report. */
async function courseRevenue(api: any, headers: Record<string, string>, courseId: number) {
  const response = await api.get('/api/admin/reports/revenue', { headers });
  expect(response.status()).toBe(200);
  const body = await response.json();
  const line = body.byCourse.find((c: { courseId: number }) => c.courseId === courseId);
  return line ?? { amountPaise: 0, paymentCount: 0, payerCount: 0 };
}

async function payments(api: any, headers: Record<string, string>, courseId: number, userId: number) {
  const response = await api.get(`/api/admin/courses/${courseId}/students/${userId}/payments`, { headers });
  expect(response.status()).toBe(200);
  return response.json();
}

test.describe('@destructive revenue comes from the ledger', () => {
  test('a tagged student is recorded at the course price', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers, 50000);

    await api.post(`/api/admin/courses/${course.id}/students`, {
      headers,
      data: { userId: student.userId },
    });

    const revenue = await courseRevenue(api, headers, course.id);
    expect(revenue.amountPaise, 'the sale is worth the course price, not zero').toBe(50000);
    expect(revenue.paymentCount).toBe(1);
    expect(revenue.payerCount).toBe(1);

    const history = await payments(api, headers, course.id, student.userId);
    expect(history).toHaveLength(1);
    expect(history[0].amountPaise).toBe(50000);
    expect(history[0].source).toBe('OFFLINE');
  });

  test('the payment history shows the reference an admin typed', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers, 50000);
    const reference = `UPI-${Date.now()}`;

    await api.post(`/api/admin/courses/${course.id}/students`, {
      headers,
      data: { userId: student.userId, razorpayTransactionId: reference },
    });

    // Not the synthetic ADMIN-ORDER-... id, which means nothing to a person.
    const history = await payments(api, headers, course.id, student.userId);
    expect(history[0].reference).toBe(reference);
  });

  test('a free seat grants access and earns nothing', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers, 50000);

    const tagged = await api.post(`/api/admin/courses/${course.id}/students`, {
      headers,
      data: { userId: student.userId, recordPayment: false },
    });
    expect(tagged.status()).toBe(201);

    // Access is real...
    expect(
      (await api.get(`/api/courses/${course.id}/videos`, { headers: authHeader(student) })).status(),
    ).toBe(200);

    // ...the money is not.
    const revenue = await courseRevenue(api, headers, course.id);
    expect(revenue.amountPaise, 'a scholarship is not income').toBe(0);

    const history = await payments(api, headers, course.id, student.userId);
    expect(history).toHaveLength(1);
    expect(history[0].source).toBe('COMPLIMENTARY');
    expect(history[0].amountPaise).toBe(0);
  });

  test('editing the price does not rewrite past sales', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers, 50000);
    await api.post(`/api/admin/courses/${course.id}/students`, { headers, data: { userId: student.userId } });

    const before = await courseRevenue(api, headers, course.id);
    expect(before.amountPaise).toBe(50000);

    const patched = await api.patch(`/api/admin/courses/${course.id}`, {
      headers,
      data: { pricePaise: 999900 },
    });
    expect(patched.status()).toBe(200);

    const after = await courseRevenue(api, headers, course.id);
    expect(after.amountPaise, 'what was already sold was sold at the old price').toBe(50000);
  });

  test('removing a student does not erase the sale', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers, 50000);
    const studentsPath = `/api/admin/courses/${course.id}/students`;
    await api.post(studentsPath, { headers, data: { userId: student.userId } });

    expect((await api.delete(`${studentsPath}/${student.userId}`, { headers })).status()).toBe(204);

    const revenue = await courseRevenue(api, headers, course.id);
    expect(revenue.amountPaise, 'the money came in; removing access does not refund it').toBe(50000);
  });

  test('paying twice counts twice, against one enrolment', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers, 50000);
    const studentsPath = `/api/admin/courses/${course.id}/students`;

    await api.post(studentsPath, { headers, data: { userId: student.userId } });
    await api.delete(`${studentsPath}/${student.userId}`, { headers });
    const again = await api.post(studentsPath, {
      headers,
      data: { userId: student.userId, recordPayment: true },
    });
    expect(again.status()).toBe(201);

    const revenue = await courseRevenue(api, headers, course.id);
    expect(revenue.amountPaise).toBe(100000);
    expect(revenue.paymentCount, 'two payments').toBe(2);
    expect(revenue.payerCount, 'one student').toBe(1);

    const roster = await api.get(studentsPath, { headers });
    const row = (await roster.json()).find((s: { id: number }) => s.id === student.userId);
    expect(row.paymentCount).toBe(2);
    expect(row.firstPaidOn, 'first payment, not the latest').not.toBeNull();
  });

  test('restoring a removed student adds no payment', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers, 50000);
    const studentsPath = `/api/admin/courses/${course.id}/students`;

    await api.post(studentsPath, { headers, data: { userId: student.userId } });
    await api.delete(`${studentsPath}/${student.userId}`, { headers });
    expect((await api.post(`${studentsPath}/${student.userId}/restore`, { headers })).status()).toBe(200);

    const revenue = await courseRevenue(api, headers, course.id);
    expect(revenue.amountPaise, 'an undo is not a sale').toBe(50000);
    expect(revenue.paymentCount).toBe(1);
  });

  test('re-tagging a removed student refuses to guess whether they paid', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers, 50000);
    const studentsPath = `/api/admin/courses/${course.id}/students`;

    await api.post(studentsPath, { headers, data: { userId: student.userId } });
    await api.delete(`${studentsPath}/${student.userId}`, { headers });

    // No recordPayment given, and the student was removed: the API must not
    // decide on its own whether this is a correction or a second sale.
    const ambiguous = await api.post(studentsPath, { headers, data: { userId: student.userId } });
    expect(ambiguous.status()).toBe(409);
    expect(await ambiguous.text()).toContain('Restore');

    const revenue = await courseRevenue(api, headers, course.id);
    expect(revenue.paymentCount, 'the refused call recorded nothing').toBe(1);
  });
});
