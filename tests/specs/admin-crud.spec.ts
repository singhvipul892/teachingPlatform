import { authHeader, destructiveTest as test, expect } from '../src/fixtures';

/**
 * @destructive — ephemeral stack only.
 *
 * These write. Course "deletion" is a soft delete (AdminCourseService.java:154)
 * and users cannot be deleted at all, so none of this can be undone. It only
 * ever runs against the tmpfs database in docker-compose.test.yml, which is
 * discarded wholesale by `npm run stack:down`.
 *
 * The fixture in src/fixtures.ts fails the test outright if the target is not
 * loopback, so a mistaken `--project=prod` run cannot reach this code.
 */
test.describe('@destructive admin course lifecycle', () => {
  const unique = () => `QA course ${Date.now()}-${Math.floor(Math.random() * 1000)}`;

  test('a course can be created, read back, updated and soft-deleted', async ({ api, admin }) => {
    const title = unique();
    const headers = authHeader(admin);

    // --- create -------------------------------------------------------------
    const created = await api.post('/api/admin/courses', {
      headers,
      multipart: { title, description: 'created by the test suite', pricePaise: '99900', currency: 'INR', active: 'true' },
    });
    expect(created.status()).toBe(201);

    const course = await created.json();
    expect(course.id).toBeGreaterThan(0);
    expect(course.title).toBe(title);
    expect(course.pricePaise).toBe(99900);

    // --- it shows up publicly ----------------------------------------------
    const publicList = await (await api.get('/api/courses')).json();
    expect(publicList.map((c: { id: number }) => c.id)).toContain(course.id);

    // --- update -------------------------------------------------------------
    const patched = await api.patch(`/api/admin/courses/${course.id}`, {
      headers,
      data: { title: `${title} (updated)`, pricePaise: 149900 },
    });
    expect(patched.status()).toBe(200);
    expect((await patched.json()).pricePaise).toBe(149900);

    // --- soft delete --------------------------------------------------------
    const deleted = await api.delete(`/api/admin/courses/${course.id}`, { headers });
    expect(deleted.status()).toBe(204);

    // Gone from the public list...
    const afterDelete = await (await api.get('/api/courses')).json();
    expect(afterDelete.map((c: { id: number }) => c.id)).not.toContain(course.id);

    // ...but still in the admin list, because the row is never removed. This
    // asserts the behaviour as it actually is, which is exactly why the
    // production suite is not allowed to create courses.
    const adminList = await (await api.get('/api/admin/courses', { headers })).json();
    expect(adminList.map((c: { id: number }) => c.id)).toContain(course.id);
  });

  test('creating a course without a price is rejected', async ({ api, admin }) => {
    const response = await api.post('/api/admin/courses', {
      headers: authHeader(admin),
      multipart: { title: unique() },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
    expect(response.status()).toBeLessThan(500);
  });

  test('updating a course that does not exist is 404', async ({ api, admin }) => {
    const response = await api.patch('/api/admin/courses/999999', {
      headers: authHeader(admin),
      data: { title: 'nope' },
    });
    expect(response.status()).toBe(404);
  });
});

test.describe('@destructive admin video lifecycle', () => {
  test('a video can be attached to a course, renamed and hard-deleted', async ({ api, admin }) => {
    const headers = authHeader(admin);

    const course = await (
      await api.post('/api/admin/courses', {
        headers,
        multipart: { title: `QA video host ${Date.now()}`, pricePaise: '50000' },
      })
    ).json();

    // No PDF parts: those upload to S3, and the test stack points at a bucket
    // that does not exist.
    const created = await api.post('/admin/videos', {
      headers,
      multipart: {
        youtubeVideoLink: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
        title: 'QA lecture 1',
        courseId: String(course.id),
        duration: '12:45',
        displayOrder: '1',
      },
    });
    expect(created.status()).toBe(201);
    const video = await created.json();
    expect(video.id).toBeGreaterThan(0);

    const renamed = await api.patch(`/admin/videos/${video.id}`, {
      headers,
      data: { title: 'QA lecture 1 (renamed)', displayOrder: 2 },
    });
    expect(renamed.status()).toBe(200);
    expect((await renamed.json()).title).toBe('QA lecture 1 (renamed)');

    // Videos, unlike courses, really are removed.
    const deleted = await api.delete(`/admin/videos/${video.id}`, { headers });
    expect(deleted.status()).toBe(204);

    const remaining = await (await api.get(`/api/admin/courses/${course.id}/videos`, { headers })).json();
    expect(remaining.map((v: { id: number }) => v.id)).not.toContain(video.id);
  });
});

test.describe('@destructive admin student registration', () => {
  test('an admin can register a student and find them again', async ({ api, admin }) => {
    const headers = authHeader(admin);
    const mobile = `9${String(Date.now()).slice(-9)}`;
    const email = `qa-offline-${Date.now()}@ephemeral.invalid`;

    const created = await api.post('/api/admin/users', {
      headers,
      data: {
        firstName: 'Offline',
        lastName: 'Payer',
        mobileNumber: mobile,
        email,
        password: 'OfflinePayer!234',
      },
    });
    expect(created.status()).toBe(201);

    const found = await api.get(`/api/admin/users?q=${encodeURIComponent(mobile)}`, { headers });
    expect(found.status()).toBe(200);
    expect((await found.json()).email).toBe(email);
  });
});

test.describe('@destructive admin student tagging', () => {
  const unique = () => `QA tagging ${Date.now()}-${Math.floor(Math.random() * 1000)}`;

  async function makeCourse(api: any, headers: Record<string, string>) {
    const created = await api.post('/api/admin/courses', {
      headers,
      multipart: {
        title: unique(),
        description: 'created by the tagging suite',
        pricePaise: '50000',
        currency: 'INR',
        active: 'true',
      },
    });
    expect(created.status()).toBe(201);
    return created.json();
  }

  const enrolledIds = async (api: any, headers: Record<string, string>, courseId: number) => {
    const response = await api.get(`/api/admin/courses/${courseId}/students`, { headers });
    expect(response.status()).toBe(200);
    return (await response.json()).map((s: { id: number }) => s.id);
  };

  const ownsCourse = async (api: any, student: { token: string }, courseId: number) => {
    const response = await api.get('/api/user/courses', { headers: authHeader(student) });
    expect(response.status()).toBe(200);
    return (await response.json()).purchasedCourses.some((c: { id: number }) => c.id === courseId);
  };

  test('untagging withdraws access without destroying the enrolment', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers);
    const studentsPath = `/api/admin/courses/${course.id}/students`;

    expect((await api.post(studentsPath, { headers, data: { userId: student.userId } })).status()).toBe(201);
    expect(await enrolledIds(api, headers, course.id)).toContain(student.userId);
    expect(await ownsCourse(api, student, course.id)).toBe(true);
    expect((await api.get(`/api/courses/${course.id}/videos`, { headers: authHeader(student) })).status()).toBe(200);

    expect((await api.delete(`${studentsPath}/${student.userId}`, { headers })).status()).toBe(204);

    // The row survives now, so the access checks have to be the thing that stops
    // them — not the absence of the row.
    expect(
      (await api.get(`/api/courses/${course.id}/videos`, { headers: authHeader(student) })).status(),
      'an untagged student must lose the content',
    ).toBe(403);
    expect(await ownsCourse(api, student, course.id), 'and it leaves their course list').toBe(false);
    expect(await enrolledIds(api, headers, course.id), 'and the admin roster').not.toContain(student.userId);

    const counts = await (await api.get(`/api/admin/courses/${course.id}`, { headers })).json();
    expect(counts.studentCount).toBe(0);
    expect(counts.activeStudentCount).toBe(0);

    // Untagging twice is still a 404 — the stamped row must not read as enrolled.
    expect((await api.delete(`${studentsPath}/${student.userId}`, { headers })).status()).toBe(404);
    // Nor may an expiry be set on someone who was removed.
    expect(
      (await api.put(`${studentsPath}/${student.userId}/expiry`, { headers, data: { expiryDate: null } })).status(),
    ).toBe(404);
  });

  test('a student removed from a course can be tagged onto it again', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers);
    const studentsPath = `/api/admin/courses/${course.id}/students`;

    expect((await api.post(studentsPath, { headers, data: { userId: student.userId } })).status()).toBe(201);
    expect((await api.delete(`${studentsPath}/${student.userId}`, { headers })).status()).toBe(204);

    // The order id used to be derived from (user, course) alone, so a second tag
    // collided with the unique constraint on razorpay_order_id and 500ed. Each
    // tag is its own offline payment and gets its own order.
    //
    // recordPayment is required here: for a student who was removed, the API
    // refuses to decide on its own whether this is a second sale or a correction.
    const again = await api.post(studentsPath, {
      headers,
      data: { userId: student.userId, recordPayment: true },
    });
    expect(again.status(), await again.text()).toBe(201);

    expect(await enrolledIds(api, headers, course.id)).toContain(student.userId);
    expect(await ownsCourse(api, student, course.id)).toBe(true);
    expect(
      (await api.get(`/api/courses/${course.id}/videos`, { headers: authHeader(student) })).status(),
      're-tagging restores the content',
    ).toBe(200);

    const counts = await (await api.get(`/api/admin/courses/${course.id}`, { headers })).json();
    expect(counts.studentCount, 'and counts them once, not twice').toBe(1);
    expect(counts.activeStudentCount).toBe(1);
  });

  test('restoring an untagged student undoes it without recording a payment', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers);
    const studentsPath = `/api/admin/courses/${course.id}/students`;

    const tagged = await api.post(studentsPath, { headers, data: { userId: student.userId } });
    expect(tagged.status()).toBe(201);
    const boughtAt = (await tagged.json()).purchasedAt;

    expect((await api.delete(`${studentsPath}/${student.userId}`, { headers })).status()).toBe(204);

    // The removed student is still listed when asked for, so the mistake is visible.
    const withRemoved = await api.get(`${studentsPath}?includeRemoved=true`, { headers });
    expect(withRemoved.status()).toBe(200);
    const removed = (await withRemoved.json()).find((s: { id: number }) => s.id === student.userId);
    expect(removed, 'a removed student stays on the roster when asked for').toBeDefined();
    expect(removed.removedOn).not.toBeNull();
    // ...but not by default.
    expect(await enrolledIds(api, headers, course.id)).not.toContain(student.userId);

    const restored = await api.post(`${studentsPath}/${student.userId}/restore`, { headers });
    expect(restored.status(), await restored.text()).toBe(200);

    // Nothing was bought, so nothing about the purchase may have moved. A tag
    // would have reset purchasedAt; a restore must not. Compared as instants
    // because the tag response carries nanoseconds straight from memory while
    // this one has been through Postgres, which keeps microseconds.
    const body = await restored.json();
    expect(new Date(body.purchasedAt).getTime(), 'restore is not a purchase').toBe(new Date(boughtAt).getTime());
    expect(body.removedOn ?? null).toBeNull();

    expect(await enrolledIds(api, headers, course.id)).toContain(student.userId);
    expect(await ownsCourse(api, student, course.id)).toBe(true);
    expect(
      (await api.get(`/api/courses/${course.id}/videos`, { headers: authHeader(student) })).status(),
    ).toBe(200);
  });

  test('restore only applies to a student who was actually removed', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers);
    const studentsPath = `/api/admin/courses/${course.id}/students`;
    const restorePath = `${studentsPath}/${student.userId}/restore`;

    // Never enrolled at all.
    expect((await api.post(restorePath, { headers })).status()).toBe(404);

    // Enrolled and not removed: there is nothing to undo.
    expect((await api.post(studentsPath, { headers, data: { userId: student.userId } })).status()).toBe(201);
    expect((await api.post(restorePath, { headers })).status()).toBe(404);

    // Removed and then restored: the second restore has nothing left to undo.
    expect((await api.delete(`${studentsPath}/${student.userId}`, { headers })).status()).toBe(204);
    expect((await api.post(restorePath, { headers })).status()).toBe(200);
    expect((await api.post(restorePath, { headers })).status()).toBe(404);
  });

  test('tagging a student who is already enrolled is rejected', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers);
    const studentsPath = `/api/admin/courses/${course.id}/students`;

    expect((await api.post(studentsPath, { headers, data: { userId: student.userId } })).status()).toBe(201);
    expect((await api.post(studentsPath, { headers, data: { userId: student.userId } })).status()).toBe(409);
  });
});
