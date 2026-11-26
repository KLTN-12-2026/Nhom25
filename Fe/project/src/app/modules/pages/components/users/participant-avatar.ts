export function avatarInitial(displayName: string | null | undefined, userName: string): string {
  const name = displayName?.trim() || userName?.trim() || '';
  const [initial] = Array.from(name.normalize('NFC'));
  return initial?.toLocaleUpperCase('vi') || '?';
}

export function avatarColor(userName: string): string {
  let hash = 0;
  for (const character of userName || '') {
    hash = (Math.imul(31, hash) + character.codePointAt(0)!) | 0;
  }
  return `hsl(${(hash >>> 0) % 360}, 55%, 32%)`;
}
