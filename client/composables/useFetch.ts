export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

const getBaseUrl = () => {
    return process.dev
        ? 'http://localhost:8080/api'
        : '/api';
}

export async function useFetch<T = unknown>(
    url: string,
    options: {
        method?: HttpMethod,
        body?: unknown,
        headers?: HeadersInit
    } = {}): Promise<T> {
    const { method = 'GET', body, headers = {}} = options;

    const res = await fetch(`${getBaseUrl()}${url}`, {
        method,
        headers: {
            'Content-Type': 'application/json',
            ...headers
        },
        body: body ? JSON.stringify(body) : undefined,
        //credentials: 'include'
    });

    if (!res.ok) {
        const text = await res.text();
        throw new Error(`API ${res.status}: ${text}`);
    }

    return (await res.json()) as T;
}
