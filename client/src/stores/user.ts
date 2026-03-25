import {defineStore} from "pinia";
import type {UserDto} from "~/models/user";
import {clearToken, getToken} from "~/utils/authToken";
import {isExpired} from "~/utils/jwt";
import {useApiFetch} from "~/composables/useApiFetch";
import {navigateTo} from "nuxt/app";

export const useUserStore = defineStore("user", {
    state: () => ({
        user: null as UserDto | null,
        loading: false,
        ready: false,
    }),

    getters: {
        isLoggedIn: (s) => !!s.user,
        roles: (s) => (s.user?.userRoles ?? []).map((r) => r.roleName),
        hasRole: (s) => (roleName: string) => (s.user?.userRoles ?? []).some((r) => r.roleName === roleName),
        isAdmin: (s) => (s.user?.userRoles ?? []).some((r) => r.roleName === "ADMIN")
    },

    actions: {
        async fetchMe(): Promise<void> {
            const token = getToken();

            if (!token) {
                this.user = null;
                return;
            }

            if (isExpired(token)) {
                clearToken();
                this.user = null;
                return;
            }

            this.loading = true;
            try {
                this.user = await useApiFetch<UserDto>("/users/me", {method: "GET"});
            } catch {
                this.user = null;
                clearToken();
            } finally {
                this.loading = false;
            }
        },

        async init(): Promise<void> {
            if (this.ready) return;
            await this.fetchMe();
            this.ready = true;
        },

        async logout(): Promise<void> {
            this.user = null;
            await navigateTo("/");
            clearToken();
        },
    },
});
