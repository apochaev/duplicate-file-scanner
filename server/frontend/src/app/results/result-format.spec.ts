import { barPercent, copiesText, headerFigures, spaceSegments } from './result-format';
import { scanEntry, setPage } from './result-test-data';

describe('result format', () => {
  const scan = scanEntry('s1', { files: 76214, bytes: 508900000000 }, ['C:\\Archive']);
  const totals = setPage([], {
    totalItems: 8412,
    totalFiles: 21930,
    totalBytes: 178100000000,
    totalReclaimableBytes: 127100000000,
  });

  it('DFS-060.AC-2 builds the four header figures in order, without thousands separators', () => {
    expect(headerFigures(scan, totals)).toEqual([
      { label: 'Reclaimable', value: '118.4 GB' },
      { label: 'Duplicate sets', value: '8412' },
      { label: 'Duplicate files', value: '21930' },
      { label: 'Scanned', value: '474.0 GB · 76214 files' },
    ]);
  });

  it('DFS-060.AC-2 says "1 file" for a scan of one file', () => {
    const tiny = scanEntry('s1', { files: 1, bytes: 10 });

    expect(headerFigures(tiny, setPage([])).map((figure) => figure.value)).toEqual(['0 B', '0', '0', '10 B · 1 file']);
  });

  it('DFS-060.AC-3 splits the scanned bytes into reclaimable, last copies kept and unique', () => {
    const segments = spaceSegments(scan, totals);

    expect(segments.map((segment) => segment.kind)).toEqual(['reclaimable', 'kept', 'unique']);
    expect(segments.map((segment) => segment.bytes)).toEqual([127100000000, 51000000000, 330800000000]);
    expect(segments[0].percent).toBeCloseTo(25.0, 0);
    expect(segments[1].percent).toBeCloseTo(10.0, 0);
    expect(segments[2].percent).toBeCloseTo(65.0, 0);
    expect(segments.map((segment) => segment.legend)).toEqual([
      'Reclaimable · 118.4 GB',
      'Last copies, always kept · 47.5 GB',
      'Unique · 308.1 GB',
    ]);
  });

  it('DFS-060.AC-3 never lets the unique segment go below 0', () => {
    const odd = scanEntry('s1', { files: 5, bytes: 1000 });
    const more = setPage([], { totalBytes: 1500, totalReclaimableBytes: 500 });

    const unique = spaceSegments(odd, more)[2];

    expect(unique.bytes).toBe(0);
    expect(unique.percent).toBe(0);
    expect(unique.legend).toBe('Unique · 0 B');
  });

  it('DFS-060.AC-3 shows three empty segments, and no division by zero, for a scan of 0 bytes', () => {
    const segments = spaceSegments(scanEntry('s1', { files: 0, bytes: 0 }), setPage([]));

    expect(segments.map((segment) => segment.percent)).toEqual([0, 0, 0]);
    expect(segments.map((segment) => segment.legend)).toEqual([
      'Reclaimable · 0 B',
      'Last copies, always kept · 0 B',
      'Unique · 0 B',
    ]);
  });

  it.each([
    [25, 100, 25],
    [1, 3, 100 / 3],
    [0, 100, 0],
    [5, 0, 0],
    [5, -1, 0],
    [200, 100, 100],
  ])('DFS-060.AC-3, DFS-060.AC-4, DFS-060.AC-5 takes %d of %d as %d percent', (part, whole, percent) => {
    expect(barPercent(part, whole)).toBeCloseTo(percent, 6);
  });

  it.each([
    [1, '1 copy'],
    [2, '2 copies'],
    [3, '3 copies'],
  ])('DFS-060.AC-5 writes %d copies as "%s"', (count, text) => {
    expect(copiesText(count)).toBe(text);
  });
});
