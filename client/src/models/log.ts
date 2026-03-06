export type LogDto = {
    id: number;
    createdAt: string;
    logLevel: string;
    username: string | null;
    message: string;
};