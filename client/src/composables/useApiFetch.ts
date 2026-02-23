import { process } from "std-env";
import { clearToken, getToken } from "~/utils/authToken";
import { isExpired } from "~/utils/jwt";
import { navigateTo} from "nuxt/app";
import type {ApiError} from "~/models/error";

export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

const getBaseUrl = (): string => {
    return process.dev ? "http://localhost:8080/api" : "/api";
};

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
            // fall through to text fallback
        }
    }

    // Fallback: plain text / HTML / anything else
    let text = "";
    try {
        text = await res.text();
    } catch {
        text = "";
    }

    const msg = text?.trim() ? text.trim() : `Request failed (${res.status})`;

    return {
        status: res.status,
        error: res.statusText || "Error",
        message: msg,
        path: url,
        details: null,
    };
}

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

    const token = getToken();
    if (token) {
        if (isExpired(token)) {
            clearToken();
        } else {
            (headers as Record<string, string>)["Authorization"] = `Bearer ${token}`;
        }
    }

    const res = await fetch(`${getBaseUrl()}${url}`, {
        method,
        headers,
        body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    });

    if (res.status === 401) {
        clearToken();
        await navigateTo("/login");

        throw await toApiError(res, url);
    }

    if (!res.ok) {
        throw await toApiError(res, url);
    }

    if (res.status === 204) {
        return undefined as T;
    }

    return (await res.json()) as T;
}
