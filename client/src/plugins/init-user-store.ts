import { useUserStore } from "~/stores/user";

export default defineNuxtPlugin(async () => {
    const userStore = useUserStore();
    await userStore.init();
});
