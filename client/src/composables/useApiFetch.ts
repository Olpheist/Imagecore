import { process } from "std-env";
import { clearToken, getToken } from "~/utils/authToken";
import { isExpired } from "~/utils/jwt";
import { navigateTo} from "nuxt/app";

export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

const getBaseUrl = (): string => {
    return process.dev ? "http://localhost:8080/api" : "/api";
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
    }

    if (!res.ok) {
        throw new Error(`API ${res.status}: ${await res.text()}`);
    }

    if (res.status === 204) {
        return undefined as T;
    }

    return (await res.json()) as T;
}
