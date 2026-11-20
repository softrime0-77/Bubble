// 추천 코드 만들기/검사 (순수 함수, 테스트용으로 분리)

/** 헷갈리는 글자(0/O, 1/I/L)를 뺀 32자 */
export const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
export const CODE_LENGTH = 6;

export function randomCode(rand: () => number = Math.random): string {
  let s = "";
  for (let i = 0; i < CODE_LENGTH; i++) s += CODE_ALPHABET[Math.floor(rand() * CODE_ALPHABET.length)];
  return s;
}

/** 사용자가 입력한 코드를 정리 (공백 제거, 대문자). 형식이 틀리면 null. */
export function normalizeCode(input: unknown): string | null {
  if (typeof input !== "string") return null;
  const c = input.replace(/\s+/g, "").toUpperCase();
  if (c.length !== CODE_LENGTH) return null;
  for (const ch of c) if (!CODE_ALPHABET.includes(ch)) return null;
  return c;
}
