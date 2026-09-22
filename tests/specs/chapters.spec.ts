import { authHeader, destructiveTest as test, expect } from '../src/fixtures';

/**
 * @destructive — ephemeral stack only (see admin-crud.spec.ts for why).
 *
 * Course → Chapter → Video. The teacher never sends an order number: new
 * chapters and classes are appended, and a drag sends the full new order.
 * Every chapter endpoint answers with the whole course content.
 */
test.describe('@destructive course chapters', () => {
  type Content = { id: number; title: string; displayOrder: number; videos: { id: number; title: string; displayOrder: number }[] }[];

  const titles = (content: Content) => content.map(c => c.title);
  const videoTitles = (content: Content, chapter: string) =>
    content.find(c => c.title === chapter)!.videos.map(v => v.title);

  async function makeCourse(api: any, headers: Record<string, string>) {
    const created = await api.post('/api/admin/courses', {
      headers,
      multipart: { title: `QA chapters ${Date.now()}`, pricePaise: '50000', active: 'true' },
    });
    expect(created.status()).toBe(201);
    return created.json();
  }

  // No PDF parts: those upload to S3, and the test stack has no bucket.
  async function addClass(api: any, headers: Record<string, string>, chapterId: number, title: string) {
    const created = await api.post('/admin/videos', {
      headers,
      multipart: {
        youtubeVideoLink: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
        title,
        chapterId: String(chapterId),
      },
    });
    expect(created.status(), await created.text()).toBe(201);
    return created.json();
  }

  test('chapters and classes are appended, reordered and moved without order numbers', async ({ api, admin, student }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers);
    const base = `/api/admin/courses/${course.id}`;

    // A pasted list, with the syllabus numbering the panel strips before sending.
    let res = await api.post(`${base}/chapters`, { headers, data: { titles: ['Percentage', 'Profit & Loss', '  '] } });
    expect(res.status()).toBe(201);
    let content: Content = await res.json();
    expect(titles(content), 'blank names are ignored').toEqual(['Percentage', 'Profit & Loss']);

    res = await api.post(`${base}/chapters`, { headers, data: { titles: ['Simple Interest'] } });
    content = await res.json();
    expect(content.map(c => c.displayOrder)).toEqual([1, 2, 3]);
    const [pct, pnl, si] = content;

    await addClass(api, headers, pct.id, 'Type 1');
    await addClass(api, headers, pct.id, 'Type 2');
    await addClass(api, headers, pct.id, 'Type 3');
    const cpsp = await addClass(api, headers, pnl.id, 'CP/SP');

    content = await (await api.get(`${base}/content`, { headers })).json();
    expect(videoTitles(content, 'Percentage'), 'appended in the order added').toEqual(['Type 1', 'Type 2', 'Type 3']);
    expect(content.find(c => c.id === pct.id)!.videos.map(v => v.displayOrder)).toEqual([1, 2, 3]);

    // Reorder chapters.
    res = await api.put(`${base}/chapters/order`, { headers, data: { chapterIds: [si.id, pct.id, pnl.id] } });
    expect(res.status()).toBe(200);
    expect(titles(await res.json())).toEqual(['Simple Interest', 'Percentage', 'Profit & Loss']);

    // A stale order (missing a chapter) is refused rather than half-applied.
    res = await api.put(`${base}/chapters/order`, { headers, data: { chapterIds: [si.id, pct.id] } });
    expect(res.status()).toBe(409);

    // Move "Type 2" into Profit & Loss, first; Percentage closes the gap.
    const pctVideos = content.find(c => c.id === pct.id)!.videos;
    const type2 = pctVideos.find(v => v.title === 'Type 2')!;
    res = await api.put(`/api/admin/chapters/${pnl.id}/videos/order`, {
      headers,
      data: { videoIds: [type2.id, cpsp.id] },
    });
    expect(res.status(), await res.text()).toBe(200);
    content = await res.json();
    expect(videoTitles(content, 'Profit & Loss')).toEqual(['Type 2', 'CP/SP']);
    expect(videoTitles(content, 'Percentage')).toEqual(['Type 1', 'Type 3']);
    expect(content.find(c => c.id === pct.id)!.videos.map(v => v.displayOrder), 'source renumbered').toEqual([1, 2]);

    // Leaving out a class already in the chapter means the panel is out of date.
    res = await api.put(`/api/admin/chapters/${pnl.id}/videos/order`, { headers, data: { videoIds: [cpsp.id] } });
    expect(res.status()).toBe(409);

    // Rename.
    res = await api.patch(`/api/admin/chapters/${pnl.id}`, { headers, data: { title: 'Profit, Loss & Discount' } });
    expect(titles(await res.json())).toContain('Profit, Loss & Discount');

    // The student sees chapters with classes only, in order; the flat list follows the same order.
    expect((await api.post(`${base}/students`, { headers, data: { userId: student.userId } })).status()).toBe(201);
    const studentHeaders = authHeader(student);
    const chapters: Content = await (await api.get(`/api/courses/${course.id}/chapters`, { headers: studentHeaders })).json();
    expect(titles(chapters), 'empty "Simple Interest" is hidden').toEqual(['Percentage', 'Profit, Loss & Discount']);
    const flat = await (await api.get(`/api/courses/${course.id}/videos`, { headers: studentHeaders })).json();
    expect(flat.map((v: { title: string }) => v.title)).toEqual(['Type 1', 'Type 3', 'Type 2', 'CP/SP']);

    // Deleting a chapter takes its classes with it.
    res = await api.delete(`/api/admin/chapters/${pct.id}`, { headers });
    expect(res.status()).toBe(200);
    content = await res.json();
    expect(titles(content)).toEqual(['Simple Interest', 'Profit, Loss & Discount']);
    expect(content.map(c => c.displayOrder)).toEqual([1, 2]);
    const remaining = await (await api.get(`${base}/videos`, { headers })).json();
    expect(remaining.map((v: { title: string }) => v.title)).toEqual(['Type 2', 'CP/SP']);
  });

  test('a class cannot be moved into another course', async ({ api, admin }) => {
    const headers = authHeader(admin);
    const a = await makeCourse(api, headers);
    const b = await makeCourse(api, headers);
    const [chA] = await (await api.post(`/api/admin/courses/${a.id}/chapters`, { headers, data: { titles: ['A'] } })).json();
    const [chB] = await (await api.post(`/api/admin/courses/${b.id}/chapters`, { headers, data: { titles: ['B'] } })).json();
    const video = await addClass(api, headers, chA.id, 'Only in A');

    const res = await api.put(`/api/admin/chapters/${chB.id}/videos/order`, { headers, data: { videoIds: [video.id] } });
    expect(res.status()).toBe(400);
  });

  test('a video posted with only a courseId lands in the first chapter', async ({ api, admin }) => {
    const headers = authHeader(admin);
    const course = await makeCourse(api, headers);

    const created = await api.post('/admin/videos', {
      headers,
      multipart: {
        youtubeVideoLink: 'https://youtu.be/dQw4w9WgXcQ',
        title: 'Legacy form',
        courseId: String(course.id),
      },
    });
    expect(created.status(), await created.text()).toBe(201);

    const content: Content = await (await api.get(`/api/admin/courses/${course.id}/content`, { headers })).json();
    expect(titles(content), 'a default chapter is created when there is none').toEqual(['All Classes']);
    expect(videoTitles(content, 'All Classes')).toEqual(['Legacy form']);
  });

  test('a link that is not YouTube is a 400, not a 500', async ({ api, admin }) => {
    const headers = authHeader(admin);
    const info = await api.get(`/admin/videos/youtube-info?url=${encodeURIComponent('https://example.com/watch')}`, { headers });
    expect(info.status()).toBe(400);
  });
});
