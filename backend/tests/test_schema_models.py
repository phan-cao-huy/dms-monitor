from app.models.auth import AuthToken, User
from app.models.organization import (
    DeviceGroup,
    Organization,
    OrganizationInvitation,
    OrganizationMember,
)


def test_auth_and_organization_models_match_dms01_tables():
    expected_columns = {
        User: {
            "user_id",
            "username",
            "email",
            "password_hash",
            "full_name",
            "avatar_url",
            "is_email_verified",
            "failed_login_count",
            "locked_until",
            "last_login_at",
            "created_at",
            "updated_at",
        },
        AuthToken: {
            "token_id",
            "user_id",
            "token_type",
            "token_hash",
            "expires_at",
            "used_at",
            "device_info",
            "fcm_token",
            "created_at",
        },
        Organization: {
            "org_id",
            "org_name",
            "description",
            "logo_url",
            "owner_id",
            "deleted_at",
            "created_at",
            "updated_at",
        },
        OrganizationMember: {
            "member_id",
            "org_id",
            "user_id",
            "role",
            "joined_at",
        },
        OrganizationInvitation: {
            "invite_id",
            "org_id",
            "invite_code",
            "default_role",
            "max_uses",
            "use_count",
            "expires_at",
            "revoked_at",
            "created_by",
            "created_at",
        },
        DeviceGroup: {
            "group_id",
            "org_id",
            "group_name",
            "description",
            "created_at",
        },
    }

    for model, expected in expected_columns.items():
        assert set(model.__table__.columns.keys()) == expected
