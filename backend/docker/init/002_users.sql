-- Users table for login/signup (auth)
create table if not exists users (
    id bigserial primary key,
    first_name varchar(100) not null,
    last_name varchar(100) not null,
    email varchar(255) not null,
    mobile_number varchar(20) not null,
    password_hash varchar(255) not null,
    created_at timestamptz not null default now(),
    constraint uq_users_email unique (email),
    constraint uq_users_mobile unique (mobile_number)
);

create index if not exists idx_users_email on users (email);
create index if not exists idx_users_mobile on users (mobile_number);
