import { getToken, clearToken } from "~/utils/authToken";
import { isExpired } from "~/utils/jwt";

// This is purely UX middleware, all routes are publicly accessible,
// but you need to be authenticated to access any of the api routes
export default defineNuxtRouteMiddleware((to) => {
    const authPages = ["/login", "/register"];   // these should redirect if logged in
    const publicPages = ["/"];                   // public, but no redirect

    const token = getToken();
    const authed = !!token && !isExpired(token);

    // If authed and on login/register -> send to dashboard
    if (authPages.includes(to.path)) {
        if (authed) {
            return navigateTo("/dashboard");
        }
        return;
    }

    // Allow public pages for everyone
    if (publicPages.includes(to.path)) {
        return;
    }

    // Protected routes below this point
    if (!token) {
        return navigateTo("/login");
    }

    if (isExpired(token)) {
        clearToken();
        return navigateTo("/login");
    }
});
