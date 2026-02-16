export interface DecodedJwt {
    sub?: string;
    uid?: string;
    roles?: string[];
    iat?: number;
    exp?: number;
    iss?: string;
    [key: string]: unknown;
}

export function decodeToken(token: string): DecodedJwt | null {
    try {
        const parts = token.split(".");
        if (parts.length !== 3) return null;

        const payload = parts[1]!;
        const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
        const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");

        return JSON.parse(atob(padded)) as DecodedJwt;
    } catch {
        return null;
    }
}

export function isExpired(token: string, skewSeconds: number = 10): boolean {
    const decoded = decodeToken(token);
    if (!decoded?.exp) return true;
    const now = Math.floor(Date.now() / 1000);
    return decoded.exp <= now + skewSeconds;
}