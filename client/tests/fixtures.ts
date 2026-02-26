import { test as base, expect, type Page } from "@playwright/test";
import { TOKEN_KEY } from "../src/utils/authToken";

type Fixtures = {
    clearStorage: () => Promise<void>;
    setToken: (token: string) => Promise<void>;
};

export const test = base.extend<Fixtures>({
    clearStorage: async ({ page, context }, use) => {
        const fn = async (): Promise<void> => {
            await context.clearCookies();
            await page.addInitScript(() => {
                localStorage.clear();
                sessionStorage.clear();
            });
        };

        await use(fn);
    },

    setToken: async ({ page }, use) => {
        const fn = async (token: string): Promise<void> => {
            await page.addInitScript(
                ({ key, value }: { key: string; value: string }) => {
                    sessionStorage.setItem(key, value);
                },
                { key: TOKEN_KEY, value: token },
            );
        };

        await use(fn);
    },
});

export { expect };