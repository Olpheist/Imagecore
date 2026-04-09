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

export const mockClinicianUser = {
    id: "4",
    username: "clinician",
    email: "clinician@example.com",
    userRoles: [{ roleName: "CLINICIAN", roleId: 4 }],
};

export const mockImages = [
    {
        id: 1,
        filename: "brain_mri.dcm",
        fileSize: 204800,
        uploadedAt: "2026-01-15T10:30:00Z",
    },
    {
        id: 2,
        filename: "chest_ct.dcm",
        fileSize: 512000,
        uploadedAt: "2026-01-16T08:00:00Z",
    },
];


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


export const mockSeriesGroups = [
  {
    key: 'imgset-001',
    imageSetId: 'imgset-001',
    displayName: 'brain_mri.dcm',
    modality: 'MR',
    bodyPart: 'Brain',
    studyDate: '2026-01-15',
    instanceCount: 1,
    status: 'COMPLETED',
    seriesInstanceUid: null,
    studyInstanceUid: '1.2.840.10008.5.1.4.1.1.4.001',
    imageIds: [1],
  },
  {
    key: '__pending__2',
    imageSetId: null,
    displayName: 'chest_ct.dcm',
    modality: null,
    bodyPart: null,
    studyDate: null,
    instanceCount: 1,
    status: 'IN_PROGRESS',
    seriesInstanceUid: null,
    studyInstanceUid: null,
    imageIds: [2],
  },
];

export const mockStudies = [
  {
    id: 1,
    filename: '/dicom-samples/mri-001/sample.dcm',
    fileSize: 204800,
    importStatus: 'COMPLETED',
    imageSetId: 'imgset-001',
    uploadedAt: '2026-03-01T10:00:00Z',
    studyInstanceUid: '1.2.840.10008.5.1.4.1.1.4.001',
    seriesInstanceUid: '1.3.6.1.4.1.9590.100.1.001',
    sopInstanceUid: 'local.sop.1',
    studyDescription: 'Brain — T1 Coronal',
    seriesDescription: 'T1 MPRAGE Post-Contrast',
    bodyPart: 'Brain',
    modality: 'MR',
    patientId: 'PT-00421',
    studyDate: '2026-03-01',
    physician: 'Dr. Apple',
    frameCount: 1,
  },
]
