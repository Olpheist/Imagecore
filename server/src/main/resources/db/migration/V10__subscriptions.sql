create table subscription_tiers (
    id bigserial primary key,
    -- FREE, PRO
    code varchar(50) not null unique,
    name varchar(100) not null,
    description text,
    -- avoid floating point math
    monthly_price_cents integer not null,
    sort_order integer not null unique,
    created_at timestamptz not null default now()
);

insert into subscription_tiers (code, name, description, monthly_price_cents, sort_order)
values
    ('FREE', 'Free', 'Get started at no cost.', 0, 1),
    ('PRO', 'Pro', 'Unlock the full experience.', 999, 2);

alter table tools
    add column required_tier_id bigint references subscription_tiers(id);

update tools
set required_tier_id = (
    select id
    from subscription_tiers
    where code = 'FREE'
)
where required_tier_id is null;

alter table tools
    alter column required_tier_id set not null;

create table user_subscriptions (
    id bigserial primary key,
    user_id bigint not null references users(id) on delete cascade,
    tier_id bigint not null references subscription_tiers(id),
    -- TRIALING, ACTIVE, PAST_DUE, CANCELED, EXPIRED, INCOMPLETE
    status varchar(30) not null,
    provider_customer_id varchar(255),
    provider_subscription_id varchar(255),
    provider_price_id varchar(255),
    current_period_start timestamptz,
    current_period_end timestamptz,
    cancel_at timestamptz,
    canceled_at timestamptz,
    ended_at timestamptz,
    auto_renew boolean not null default true,
    is_active boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index ux_user_subscriptions_one_active
    on user_subscriptions(user_id)
    where is_active = true;

create unique index ux_user_subscriptions_provider_subscription_id
    on user_subscriptions(provider_subscription_id)
    where provider_subscription_id is not null;

create table billing_events (
    id bigserial primary key,
    provider_event_id varchar(255),
    event_type varchar(100) not null,
    user_subscription_id bigint references user_subscriptions(id) on delete set null,
    payload jsonb,
    processed_at timestamptz,
    created_at timestamptz not null default now()
);

create unique index ux_billing_events_provider_event_id
    on billing_events(provider_event_id)
    where provider_event_id is not null;

insert into user_subscriptions (
    user_id,
    tier_id,
    status,
    auto_renew,
    is_active
)
select
    u.id,
    st.id,
    'ACTIVE',
    false,
    true
from users u
         join subscription_tiers st on st.code = 'FREE'
where not exists (
    select 1
    from user_subscriptions us
    where us.user_id = u.id
      and us.is_active = true
);