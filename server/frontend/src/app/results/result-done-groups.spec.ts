import {
  countMarksUnder,
  doneStorageKey,
  DoneStorage,
  groupKey,
  readDoneMarks,
  writeDoneMarks,
} from './result-done-groups';

function storageWith(value: string | null): DoneStorage & { saved: Map<string, string> } {
  const saved = new Map<string, string>();
  return {
    saved,
    getItem: (key) => (key === 'dfs.results.done.s1' ? value : null),
    setItem: (key, v) => void saved.set(key, v),
  };
}

const THROWING: DoneStorage = {
  getItem: () => {
    throw new Error('storage disabled');
  },
  setItem: () => {
    throw new Error('storage disabled');
  },
};

describe('result-done-groups', () => {
  it('DFS-069.AC-4 builds the storage key from the scan ID', () => {
    expect(doneStorageKey('s1')).toBe('dfs.results.done.s1');
    expect(doneStorageKey('s2')).toBe('dfs.results.done.s2');
  });

  it('DFS-069.AC-4 builds the group key from the sorted folder paths joined with a line feed', () => {
    expect(groupKey(['/b/x', '/a/x'])).toBe('/a/x\n/b/x');
    expect(groupKey(['/a/x', '/b/x'])).toBe('/a/x\n/b/x');
    expect(groupKey(['/a/x'])).toBe('/a/x');
  });

  it('DFS-069.AC-4 reads back what it wrote, as a JSON array under the scan key', () => {
    const storage = storageWith(null);
    writeDoneMarks(storage, 's1', ['/a/x\n/b/x', '/a/y\n/b/y']);

    expect(JSON.parse(storage.saved.get('dfs.results.done.s1') ?? 'null')).toEqual(['/a/x\n/b/x', '/a/y\n/b/y']);
    expect(readDoneMarks(storageWith(storage.saved.get('dfs.results.done.s1') ?? null), 's1')).toEqual([
      '/a/x\n/b/x',
      '/a/y\n/b/y',
    ]);
  });

  it('DFS-069.AC-6 reads no marks from a missing value or a null storage', () => {
    expect(readDoneMarks(storageWith(null), 's1')).toEqual([]);
    expect(readDoneMarks(null, 's1')).toEqual([]);
  });

  it('DFS-069.AC-6 reads no marks from a storage that throws', () => {
    expect(readDoneMarks(THROWING, 's1')).toEqual([]);
  });

  it('DFS-069.AC-6 reads no marks from a value that is not a JSON array of strings', () => {
    for (const value of ['{not json', '{"a":1}', '[1,2]', '["a",1]', '"text"', 'null']) {
      expect(readDoneMarks(storageWith(value), 's1'), value).toEqual([]);
    }
  });

  it('DFS-069.AC-6 reads the marks of a valid array of strings', () => {
    expect(readDoneMarks(storageWith('["/a\\n/b"]'), 's1')).toEqual(['/a\n/b']);
  });

  it('DFS-069.AC-6 writes nothing to a null storage and ignores a setItem that throws', () => {
    writeDoneMarks(null, 's1', ['/a']);
    writeDoneMarks(THROWING, 's1', ['/a']);
    // Reached only when neither call threw; the stub's error fails the test before this line.
    expect(readDoneMarks(null, 's1')).toEqual([]);
  });

  it('DFS-069.AC-3 counts every key for "All folders"', () => {
    expect(countMarksUnder(['/a/x\n/b/x', '/a/y\n/b/y', '/a/z\n/c/z'], null)).toBe(3);
    expect(countMarksUnder([], null)).toBe(0);
  });

  it('DFS-069.AC-3 counts a key when any of its folders equals or lies below the folder', () => {
    const keys = ['/a/x\n/b/x', '/a/y\n/b/y', '/a/z\n/c/z'];
    expect(countMarksUnder(keys, '/b')).toBe(2);
    expect(countMarksUnder(keys, '/b/x')).toBe(1);
    expect(countMarksUnder(keys, '/c')).toBe(1);
    expect(countMarksUnder(keys, '/d')).toBe(0);
  });

  it('DFS-069.AC-3 does not count a sibling that only shares the folder name as a prefix, ignores trailing separators, is case-sensitive and takes backslashes', () => {
    expect(countMarksUnder(['/a/xy\n/b/xy'], '/a/x')).toBe(0);
    expect(countMarksUnder(['/a/x\n/b/x'], '/a/')).toBe(1);
    expect(countMarksUnder(['/a/x\n/b/x'], '/A')).toBe(0);
    expect(countMarksUnder(['D:\\a\\x\nD:\\b\\x'], 'D:\\a')).toBe(1);
  });
});
