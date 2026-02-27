export const TOKEN_KEY = "imagecore.jwt";

export const mockMeUser = {
    id: "1",
    username: "testuser",
    email: "test@example.com",
    userRoles: [{ roleName: "PATIENT", roleId: 1 }]
};

export const mockResearcherUser = {
    id: "2",
    username: "researcher",
    email: "researcher@example.com",
    userRoles: [{ roleName: "RESEARCHER", roleId: 2 }],
};

export const mockAdminUser = {
    id: "3",
    username: "admin",
    email: "admin@example.com",
    userRoles: [{ roleName: "ADMIN", roleId: 3 }],
};


export const makeJwt = (expSecondsFromNow = 3600): string => {
    const base64url = (obj: object): string =>
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

export const mockTools = [
    {
        toolId: 1,
        name: "Organ Segmentation",
        description: "Automatically delineate organ boundaries in CT and MRI volumes.",
        category: "Segmentation",
        imageTag: null,
    },
    {
        toolId: 2,
        name: "Tumor Segmentation",
        description: "Detect and segment tumor regions from multimodal imaging data.",
        category: "Segmentation",
        imageTag: null,
    },
    {
        toolId: 3,
        name: "Rigid Registration",
        description: "Align imaging volumes using rigid-body transformations.",
        category: "Registration",
        imageTag: null,
    },
    {
        toolId: 4,
        name: "Deformable Registration",
        description: "Non-linear registration for aligning anatomical structures across scans.",
        category: "Registration",
        imageTag: null,
    },
    {
        toolId: 5,
        name: "DICOM Viewer",
        description: "Visualize and inspect DICOM series with windowing and multi-planar reconstruction.",
        category: "Visualization",
        imageTag: null,
    },
    {
        toolId: 6,
        name: "Lesion Classifier",
        description: "Classify detected lesions by type and malignancy likelihood.",
        category: "Classification",
        imageTag: null,
    },
];
