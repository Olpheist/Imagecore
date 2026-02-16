export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

const getBaseUrl = () => {
    return process.dev ? "http://localhost:8080/api" : "/api";
};

const readCookie = (name: string): string | undefined => {
    if (typeof document === "undefined") return undefined;
    const match = document.cookie.match(new RegExp(`(^| )${name}=([^;]+)`));
    return match ? decodeURIComponent(match[2]) : undefined;
};

export async function useApiFetch<T = unknown>(
    url: string,
    options: {
        method?: HttpMethod;
        body?: unknown;
        headers?: HeadersInit;
    } = {},
): Promise<T> {
    const method = options.method ?? "GET";

    const headers: HeadersInit = {
        "Content-Type": "application/json",
        ...options.headers,
    };

    // Add CSRF token for non-GET requests
    if (method !== "GET") {
        await fetch(`${getBaseUrl()}/auth/csrf`, { credentials: "include" });

        const xsrf = readCookie("XSRF-TOKEN");
        if (!xsrf) throw new Error("Failed to obtain CSRF token");

        (headers as Record<string, string>)["X-XSRF-TOKEN"] = xsrf;
    }

    const res = await fetch(`${getBaseUrl()}${url}`, {
        method,
        headers,
        body: options.body ? JSON.stringify(options.body) : undefined,
        credentials: "include",
    });

    if (!res.ok) {
        throw new Error(`API ${res.status}: ${await res.text()}`);
    }

    return res.status === 204 ? undefined as T : await res.json();
}