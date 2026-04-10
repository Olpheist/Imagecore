export interface UserRoleDto {
    roleId: number;
    roleName: string;
}

export interface UserSubscriptionDto {
    tierCode: string;
    autoRenew: boolean;
    currentPeriodStart: string | null;
    currentPeriodEnd: string | null;
}

export interface UserDto {
    id: number;
    email: string;
    username: string;
    enabled: boolean;
    createdAt: string; // ISO string
    userRoles: UserRoleDto[];
    subscription: UserSubscriptionDto
}