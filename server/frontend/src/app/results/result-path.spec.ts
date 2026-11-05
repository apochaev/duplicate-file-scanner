import { containingDirectory, isPathUnderFolder } from './result-path';

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
