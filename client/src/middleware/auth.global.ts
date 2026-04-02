import { useUserStore } from "~/stores/user";

// This is purely UX middleware, all routes are publicly accessible,
// but you need to be authenticated to access any of the api routes
export default defineNuxtRouteMiddleware(async (to) => {
    const authPages = ["/login", "/register"];
    const publicPages = ["/", "/forgot-password", "/reset-password"];

    const userStore = useUserStore();

    if (!userStore.ready && !userStore.loading) {
        await userStore.init();
    }

    const authed = userStore.isLoggedIn;

    if (authPages.includes(to.path)) {
        if (authed) {
            return navigateTo("/dashboard");
        }

        return;
    }

    if (publicPages.includes(to.path)) {
        return;
    }

    if (!authed) {
        return navigateTo("/login");
    }
});
