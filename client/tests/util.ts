export const makeJwt = (expSecondsFromNow = 3600): string => {
    const base64url = (obj: object) =>
        btoa(JSON.stringify(obj))
            .replace(/\+/g, "-")
            .replace(/\//g, "_")
            .replace(/=+$/, "");

    const header = base64url({ alg: "none", typ: "JWT" });
    const payload = base64url({
        sub: "1",
        exp: Math.floor(Date.now() / 1000) + expSecondsFromNow,
    });

    return `${header}.${payload}.`;
};