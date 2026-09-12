import { authHeader, expect, test } from '../src/fixtures';

/**
 * Contract checks on the public catalogue. These exist mainly to protect the
 * Android app: it deserialises these payloads into Kotlin data classes, so a
 * renamed or dropped field is a crash on a device you cannot hot-fix.
 */
test.describe('@readonly public course catalogue', () => {
  test('GET /api/courses returns well-formed courses', async ({ api }) => {
    const response = await api.get('/api/courses');
    expect(response.status()).toBe(200);

    const courses = await response.json();
    expect(Array.isArray(courses)).toBe(true);

    for (const course of courses) {
      expect(typeof course.id).toBe('number');
      expect(typeof course.title).toBe('string');
      expect(course.title.length).toBeGreaterThan(0);
      expect(Number.isInteger(course.pricePaise)).toBe(true);
      expect(course.pricePaise).toBeGreaterThanOrEqual(0);
      expect(typeof course.currency).toBe('string');
    }
  });

  test('prices are in paise, not rupees', async ({ api }) => {
    // A course priced at 999 instead of 99900 would charge a student ₹9.99.
    // Cheap to assert, expensive to discover from a support message.
    const courses = await (await api.get('/api/courses')).json();
    test.skip(courses.length === 0, 'no courses published yet');

    for (const course of courses) {
      expect(
        course.pricePaise,
        `"${course.title}" is priced at ${course.pricePaise} paise — suspiciously like rupees`,
      ).toBeGreaterThanOrEqual(100);
    }
  });

  test('the public list never leaks inactive courses', async ({ api }) => {
    // deleteCourse is a soft delete (active=false). If the public query stops
    // filtering on it, deleted courses reappear in the app and the website.
    const courses = await (await api.get('/api/courses')).json();
    for (const course of courses) {
      if ('active' in course) expect(course.active).not.toBe(false);
    }
  });

  test('GET /api/user/courses has the shape the app expects', async ({ api, student }) => {
    const response = await api.get('/api/user/courses', { headers: authHeader(student) });
    expect(response.status()).toBe(200);

    const body = await response.json();
    expect(body).not.toBeNull();
    expect(typeof body).toBe('object');
  });
});
