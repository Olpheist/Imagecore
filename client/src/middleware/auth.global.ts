import { getToken, clearToken } from "~/utils/authToken";
import { isExpired } from "~/utils/jwt";

// This is purely UX middleware, all routes are publicly accessible,
// but you need to be authenticated to access any of the api routes
export default defineNuxtRouteMiddleware((to) => {
    const publicRoutes = ["/login", "/register"];
    const token = getToken();

    // If user is authenticated and tries to access login/register -> redirect home
    if (publicRoutes.includes(to.path)) {
        if (token && !isExpired(token)) {
            return navigateTo("/");
        }
        return;
    }

    // No token -> go to login
    if (!token) {
        return navigateTo("/login");
    }

    // Expired token -> clear + go to login
    if (isExpired(token)) {
        clearToken();
        return navigateTo("/login");
    }
});
