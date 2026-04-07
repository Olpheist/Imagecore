import { process } from "std-env";
import { navigateTo } from "nuxt/app";
import { useUserStore } from "~/stores/user";
import type { ApiError } from "~/models/error";

export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export const getBaseUrl = (): string => {
    return process.dev ? "http://localhost:8080/api" : "/api";
};

let csrfPromise: Promise<void> | null = null;

function getCookie(name: string): string | null {
    if (typeof document === "undefined") return null;

    const cookies = document.cookie ? document.cookie.split("; ") : [];

    for (const cookie of cookies) {
        if (cookie.startsWith(`${name}=`)) {
            return decodeURIComponent(cookie.substring(name.length + 1));
        }
    }

    return null;
}

function isUnsafeMethod(method: HttpMethod): boolean {
    return method === "POST" || method === "PUT" || method === "PATCH" || method === "DELETE";
}

async function toApiError(res: Response, url: string): Promise<ApiError> {
    const contentType = res.headers.get("content-type") ?? "";

    if (contentType.includes("application/json")) {
        try {
            const data = (await res.json()) as Partial<ApiError> | null;

            if (data && typeof data === "object") {
                return {
                    status: (data.status ?? res.status) as number,
                    error: data.error,
                    message: data.message ?? `Request failed (${res.status})`,
                    path: data.path ?? url,
                    details: data.details ?? null,
                    timestamp: data.timestamp,
                };
            }
        } catch {
        }
    }

    let text = "";
    try {
        text = await res.text();
    } catch {
        text = "";
    }

    const message = text.trim() ? text.trim() : `Request failed (${res.status})`;

    return {
        status: res.status,
        error: res.statusText || "Error",
        message,
        path: url,
        details: null,
    };
}

export async function ensureCsrfCookie(): Promise<void> {
    if (typeof window === "undefined") return;

    if (!csrfPromise) {
        csrfPromise = (async () => {
            const res = await fetch(`${getBaseUrl()}/auth/csrf`, {
                method: "GET",
                credentials: "include",
            });

            if (!res.ok) {
                throw await toApiError(res, "/auth/csrf");
            }
        })().finally(() => {
            csrfPromise = null;
        });
    }

    await csrfPromise;
}

export async function useApiFetch<T = unknown>(
    url: string,
    options: {
        method?: HttpMethod;
        body?: BodyInit | Record<string, unknown> | null;
        headers?: HeadersInit;
    } = {},
): Promise<T> {
    const method = options.method ?? "GET";

    if (isUnsafeMethod(method)) {
        await ensureCsrfCookie();
    }

    const headers = new Headers(options.headers);
    const body = options.body;

    const isFormData = typeof FormData !== "undefined" && body instanceof FormData;
    const isBlob = typeof Blob !== "undefined" && body instanceof Blob;
    const isUrlSearchParams = typeof URLSearchParams !== "undefined" && body instanceof URLSearchParams;
    const isJsonBody =
        body !== undefined &&
        body !== null &&
        !isFormData &&
        !isBlob &&
        !isUrlSearchParams &&
        typeof body === "object";

    if (isJsonBody && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }

    if (isUnsafeMethod(method)) {
        const csrfToken = getCookie("XSRF-TOKEN");

        if (csrfToken) {
            headers.set("X-XSRF-TOKEN", csrfToken);
        }
    }

    const res = await fetch(`${getBaseUrl()}${url}`, {
        method,
        headers,
        credentials: "include",
        body: isJsonBody ? JSON.stringify(body) : (body as BodyInit | null | undefined),
    });

    if (res.status === 401) {
        const userStore = useUserStore();
        userStore.user = null;
        userStore.ready = false;
        await navigateTo("/login");
        throw await toApiError(res, url);
    }

    if (!res.ok) {
        throw await toApiError(res, url);
    }

    if (res.status === 204) {
        return undefined as T;
    }

    const contentType = res.headers.get("content-type") ?? "";
    if (!contentType.includes("application/json")) {
        return undefined as T;
    }

    return (await res.json()) as T;
}