export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

const getBaseUrl = () => {
    return process.dev ? "http://localhost:8080/api" : "/api";
};

const readCookie = (name: string): string | undefined => {
    if (typeof document === "undefined") return undefined;
    const match = document.cookie.match(new RegExp(`(^| )${name}=([^;]+)`));
    return match ? decodeURIComponent(match[2]) : undefined;
};

const ensureCsrfCookie = async (): Promise<void> => {
    const existing = readCookie("XSRF-TOKEN");
    if (existing) return;

    await fetch(`${getBaseUrl()}/auth/csrf`, {
        method: "GET",
        credentials: "include",
    });
};

export async function useApiFetch<T = unknown>(
    url: string,
    options: {
        method?: HttpMethod;
        body?: unknown;
        headers?: HeadersInit;
    } = {},
): Promise<T> {
    const { method = "GET", body, headers = {} } = options;

    if (method !== "GET") {
        await ensureCsrfCookie();
    }

    const xsrf = readCookie("XSRF-TOKEN");

    const res = await fetch(`${getBaseUrl()}${url}`, {
        method,
        headers: {
            "Content-Type": "application/json",
            ...(xsrf ? { "X-XSRF-TOKEN": xsrf } : {}),
            ...headers,
        },
        body: body ? JSON.stringify(body) : undefined,
        credentials: "include",
    });

    if (!res.ok) {
        const text = await res.text();
        throw new Error(`API ${res.status}: ${text}`);
    }

    if (res.status === 204) {
        return undefined as T;
    }

    return (await res.json()) as T;
}
