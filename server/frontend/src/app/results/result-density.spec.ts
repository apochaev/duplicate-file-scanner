import { DENSITY_KEY, DensityStorage, browserStorage, readDensity, writeDensity } from './result-density';

/** A storage backed by a Map, optionally throwing from `getItem` or `setItem`. */
class FakeStorage implements DensityStorage {
  readonly items = new Map<string, string>();
  throwOnGet = false;
  throwOnSet = false;

  getItem(key: string): string | null {
    if (this.throwOnGet) throw new DOMException('blocked', 'SecurityError');
    return this.items.get(key) ?? null;
  }

  setItem(key: string, value: string): void {
    if (this.throwOnSet) throw new DOMException('full', 'QuotaExceededError');
    this.items.set(key, value);
  }
}

describe('result density storage', () => {
  it('DFS-060.AC-9 starts Compact when nothing is stored', () => {
    expect(readDensity(new FakeStorage())).toBe('compact');
  });

  it.each(['compact', 'comfortable'] as const)('DFS-060.AC-9 reads %s back from the key %s', (density) => {
    const storage = new FakeStorage();
    storage.items.set(DENSITY_KEY, density);

    expect(readDensity(storage)).toBe(density);
  });

  it('DFS-060.AC-9 restores the density a write stored', () => {
    const storage = new FakeStorage();

    writeDensity(storage, 'comfortable');

    expect(storage.items.get(DENSITY_KEY)).toBe('comfortable');
    expect(readDensity(storage)).toBe('comfortable');
  });

  it.each(['', 'Comfortable', 'roomy', 'null'])('DFS-060.AC-9 reads %j as Compact', (stored) => {
    const storage = new FakeStorage();
    storage.items.set(DENSITY_KEY, stored);

    expect(readDensity(storage)).toBe('compact');
  });

  it('DFS-060.AC-9 starts Compact without a storage or when reading throws', () => {
    expect(readDensity(null)).toBe('compact');
    const blocked = new FakeStorage();
    blocked.items.set(DENSITY_KEY, 'comfortable');
    blocked.throwOnGet = true;

    expect(readDensity(blocked)).toBe('compact');
  });

  it('DFS-060.AC-9 ignores a missing storage and a write that throws', () => {
    // Called directly, not inside expect(...).not.toThrow(): a throw fails the test on its own.
    const missing = writeDensity(null, 'comfortable');
    const full = new FakeStorage();
    full.items.set(DENSITY_KEY, 'compact');
    full.throwOnSet = true;

    const failed = writeDensity(full, 'comfortable');

    expect(missing).toBeUndefined();
    expect(failed).toBeUndefined();
    expect(full.items.get(DENSITY_KEY)).toBe('compact');
  });

  it('DFS-060.AC-9 offers the browser local storage by default', () => {
    const storage = browserStorage();

    expect(storage).not.toBeNull();
    expect(typeof storage?.getItem).toBe('function');
    expect(typeof storage?.setItem).toBe('function');
  });
});
