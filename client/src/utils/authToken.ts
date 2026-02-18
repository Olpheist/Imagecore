const TOKEN_KEY = "imagecore.jwt";

export function getToken(): string | undefined {
    if (typeof window === "undefined") return undefined;
    return sessionStorage.getItem(TOKEN_KEY) ?? undefined;
}

export function setToken(token: string): void {
    if (typeof window === "undefined") return;
    sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
    if (typeof window === "undefined") return;
    sessionStorage.removeItem(TOKEN_KEY);
}