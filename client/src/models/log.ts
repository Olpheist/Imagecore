export type LogDto = {
    id: number;
    createdAt: string;
    logLevel: string;
    username: string | null;
    method: string | null;
    path: string | null;
    status: number | null;
    durationMs: number | null;
    message: string;
};