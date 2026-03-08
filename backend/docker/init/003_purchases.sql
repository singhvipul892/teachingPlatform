-- Purchasable courses
create table if not exists courses (
    id            bigserial primary key,
    title         varchar(200) not null,
    description   text         not null default '',
    price_paise   integer      not null,
    currency      varchar(10)  not null default 'INR',
    thumbnail_url varchar(500) not null default '',
    active        boolean      not null default true,
    created_at    timestamptz  not null default now()
);

create index if not exists idx_courses_active on courses(active);

-- Now that courses exists, add FK from videos and enforce the relationship
do $$ begin
  if not exists (
    select 1 from information_schema.table_constraints
    where constraint_name = 'fk_videos_course' and table_name = 'videos'
  ) then
    alter table videos add constraint fk_videos_course foreign key (course_id) references courses(id);
  end if;
end $$;

-- Tracks Razorpay orders created by the backend
create table if not exists payment_orders (
    id                bigserial primary key,
    razorpay_order_id varchar(100) not null unique,
    user_id           bigint       not null references users(id),
    course_id         bigint       not null references courses(id),
    amount_paise      integer      not null,
    currency          varchar(10)  not null default 'INR',
    status            varchar(20)  not null default 'CREATED',
    created_at        timestamptz  not null default now()
);

create index if not exists idx_payment_orders_user_id           on payment_orders(user_id);
create index if not exists idx_payment_orders_razorpay_order_id on payment_orders(razorpay_order_id);
create index if not exists idx_payment_orders_course_id         on payment_orders(course_id);

-- Tracks verified, successful purchases
create table if not exists purchases (
    id                   bigserial primary key,
    user_id              bigint       not null references users(id),
    course_id            bigint       not null references courses(id),
    razorpay_order_id    varchar(100) not null unique references payment_orders(razorpay_order_id),
    razorpay_payment_id  varchar(100) not null unique,
    amount_paise         integer      not null,
    currency             varchar(10)  not null default 'INR',
    purchased_at         timestamptz  not null default now()
);

create index if not exists idx_purchases_user_id    on purchases(user_id);
create index if not exists idx_purchases_course_id  on purchases(course_id);
create unique index if not exists idx_purchases_user_course on purchases(user_id, course_id);
