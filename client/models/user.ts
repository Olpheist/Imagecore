export interface UserRoleDto {
    roleId: number;
    roleName: string;
}

export interface UserDto {
    id: number;
    email: string;
    username: string;
    enabled: boolean;
    createdAt: string; // ISO string
    userRoles: UserRoleDto[];
}