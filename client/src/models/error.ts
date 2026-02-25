export interface ApiError {
    status?: number;
    error?: string;
    message: string;
    path?: string;
    details?: string[] | null;
    timestamp?: string;
}