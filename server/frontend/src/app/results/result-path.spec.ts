import { containingDirectory, displayPath, fileName, isPathUnderFolder } from './result-path';

describe('result paths', () => {
  it.each([
    ['C:\\Photos\\a.jpg', 'C:\\Photos'],
    ['C:\\a.jpg', 'C:\\'],
    ['\\\\nas\\share\\a.jpg', '\\\\nas\\share\\'],
    ['/photos/a.jpg', '/photos'],
    ['/a.jpg', '/'],
    ['C:\\Family Photos\\été\\a.jpg', 'C:\\Family Photos\\été'],
    ['D:\\Aliases\\a.jpg', 'D:\\Aliases'],
  ])('DFS-049.AC-11 takes the containing directory of %s as %s', (file, folder) => {
    expect(containingDirectory(file)).toBe(folder);
  });

  it.each([
    ['C:\\Photos\\a.jpg', 'C:\\Photos', true],
    ['C:\\Photos\\sub\\a.jpg', 'C:\\Photos', true],
    ['C:\\Photos', 'C:\\Photos', true],
    ['C:\\Photos2\\a.jpg', 'C:\\Photos', false],
    ['D:\\Backup\\a.jpg', 'C:\\Photos', false],
    ['/photos/a.jpg', '/photos', true],
    ['/photos2/a.jpg', '/photos', false],
    ['C:\\a.jpg', 'C:\\', true],
    ['/a.jpg', '/', true],
  ])('DFS-049.AC-8 says %s under %s is %s', (path, folder, expected) => {
    expect(isPathUnderFolder(path, folder)).toBe(expected);
  });
});

describe('displayPath', () => {
  const archive = ['C:\\Archive'];
  const budget = 36;

  it.each([
    [archive, 'C:\\Archive\\Video\\GoPro\\GX01.MP4', 'Video\\GoPro\\GX01.MP4'],
    [
      archive,
      'C:\\Archive\\Backups\\MacBook-2020\\Users\\me\\Pictures\\Iceland 2019\\pano.tif',
      'Backups\\…\\Iceland 2019\\pano.tif',
    ],
    [['C:\\Archive', 'D:\\Backup'], 'D:\\Backup\\Photos\\cat.jpg', 'Backup\\Photos\\cat.jpg'],
  ])('DFS-060.AC-7 shows %j and %s as %s', (roots, path, shown) => {
    const short = displayPath(path, roots, budget);

    expect(short.text).toBe(shown);
    expect(short.folders + short.name).toBe(shown);
  });

  it('DFS-060.AC-7 splits the shown path into its folders and its file name', () => {
    const short = displayPath(
      'C:\\Archive\\Backups\\MacBook-2020\\Users\\me\\Pictures\\Iceland 2019\\pano.tif',
      archive,
      budget,
    );

    expect(short.folders).toBe('Backups\\…\\Iceland 2019\\');
    expect(short.name).toBe('pano.tif');
  });

  it('DFS-060.AC-7 cuts a file name too long alone in its middle and keeps its extension', () => {
    const short = displayPath(
      'C:\\Archive\\Downloads\\Screen Recording 2024-03-05 at 12.41.07 long.mov',
      archive,
      budget,
    );

    expect(short.text).toHaveLength(36);
    expect(short.text.startsWith('Downloads\\Screen')).toBe(true);
    expect(short.text.split('…')).toHaveLength(2);
    expect(short.text.endsWith('long.mov')).toBe(true);
    expect(short.text).toBe('Downloads\\Screen Reco…41.07 long.mov');
    expect(short.folders).toBe('Downloads\\');
  });

  it('DFS-060.AC-7 shows a path that fits the budget whole, and one of exactly the budget too', () => {
    expect(displayPath('C:\\Archive\\a\\b.txt', archive, budget).text).toBe('a\\b.txt');
    const exact = 'abcdefghijklmnopqrstuvwxyz.txt'.padEnd(36, 'x').slice(0, 36);
    expect(displayPath(`C:\\Archive\\${exact}`, archive, 36).text).toBe(exact);
  });

  it('DFS-060.AC-7 gives a file directly in its root empty folders', () => {
    const short = displayPath('C:\\Archive\\ubuntu.iso', archive, budget);

    expect(short.folders).toBe('');
    expect(short.name).toBe('ubuntu.iso');
    expect(short.text).toBe('ubuntu.iso');
  });

  it('DFS-060.AC-7 keeps the first folder and the file name and drops every middle folder that does not fit', () => {
    const short = displayPath('C:\\Archive\\one\\two\\three\\four\\five\\six\\seven\\file.bin', archive, 20);

    expect(short.text.startsWith('one\\…\\')).toBe(true);
    expect(short.text.endsWith('\\file.bin')).toBe(true);
    expect(short.text).not.toContain('two');
  });

  it('DFS-060.AC-7 uses the path\'s own separator on a POSIX path', () => {
    const short = displayPath('/data/archive/a/b.txt', ['/data/archive'], budget);

    expect(short.text).toBe('a/b.txt');
  });

  it('DFS-060.AC-7 prefixes the root\'s last name element only when the scan has several roots', () => {
    expect(displayPath('/data/archive/a/b.txt', ['/data/archive', '/mnt/backup'], budget).text).toBe('archive/a/b.txt');
  });

  it('DFS-060.AC-7 shows a path under no root whole and absolute', () => {
    expect(displayPath('E:\\Other\\x.txt', archive, budget).text).toBe('E:\\Other\\x.txt');
  });

  it('DFS-060.AC-7 spells a root without a name element without its trailing separator', () => {
    expect(displayPath('C:\\a\\b.jpg', ['C:\\', 'D:\\x'], budget).text).toBe('C:\\a\\b.jpg');
    expect(displayPath('C:\\b.jpg', ['C:\\'], budget).text).toBe('b.jpg');
  });
});

describe('fileName', () => {
  it.each([
    ['C:\\Archive\\Downloads\\ubuntu.iso', 'ubuntu.iso'],
    ['C:\\Archive\\Backups\\old-drive', 'old-drive'],
    ['/photos/a.jpg', 'a.jpg'],
    ['C:\\', 'C:\\'],
    ['/', '/'],
  ])('DFS-060.AC-5, DFS-060.AC-8 takes the last name element of %s as %s', (path, name) => {
    expect(fileName(path)).toBe(name);
  });
});
