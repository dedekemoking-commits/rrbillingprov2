-- Uang laci + shift kasir. Jalankan di Supabase SQL Editor.
-- Satu rental hanya boleh memiliki satu shift AKTIF.

create table if not exists public.shifts (
    id text primary key,
    rental_username text not null,
    username text not null,
    previous_shift_id text,
    mulai timestamptz not null,
    selesai timestamptz,
    kas_awal integer not null default 0,
    kas_akhir integer not null default 0,
    kas_seharusnya integer not null default 0,
    selisih integer not null default 0,
    total_penjualan_tunai integer not null default 0,
    total_transaksi integer not null default 0,
    total_paket integer not null default 0,
    total_fnb integer not null default 0,
    total_qris integer not null default 0,
    kas_masuk integer not null default 0,
    kas_keluar integer not null default 0,
    total_admin_withdraw integer not null default 0,
    status text not null default 'AKTIF',
    catatan text not null default '',
    tanggal_operasional text not null default '',
    jenis_shift text not null default '',
    ditutup_oleh text not null default '',
    updated_at timestamptz not null default now()
);

create unique index if not exists shifts_one_active_per_rental
    on public.shifts (rental_username)
    where status = 'AKTIF';

create index if not exists shifts_rental_mulai_idx
    on public.shifts (rental_username, mulai desc);

create table if not exists public.uang_laci (
    id text primary key,
    shift_id text not null references public.shifts(id) on delete cascade,
    rental_username text not null,
    username text not null,
    waktu timestamptz not null,
    tipe text not null default 'MASUK',
    kategori text not null default 'MANUAL',
    nominal integer not null default 0,
    saldo_setelah integer not null default 0,
    keterangan text not null default '',
    reference_id text,
    updated_at timestamptz not null default now()
);

create index if not exists uang_laci_shift_idx
    on public.uang_laci (shift_id, waktu);

alter table public.transaksi
    add column if not exists shift_id text,
    add column if not exists metode text not null default '',
    add column if not exists cash integer not null default 0,
    add column if not exists kembalian integer not null default 0;

create index if not exists transaksi_shift_id_idx
    on public.transaksi (shift_id);

create or replace view public.riwayat_shift as
select
    s.*,
    s.kas_akhir - s.kas_seharusnya as selisih_hitung
from public.shifts s
where s.status = 'SELESAI';

create or replace view public.riwayat_transaksi_shift as
select
    t.*,
    s.username as shift_username,
    s.rental_username as shift_rental_username,
    s.mulai as shift_mulai,
    s.selesai as shift_selesai,
    s.jenis_shift as shift_jenis,
    s.tanggal_operasional as shift_tanggal_operasional
from public.transaksi t
join public.shifts s on s.id = t.shift_id;

alter table public.shifts enable row level security;
alter table public.uang_laci enable row level security;

drop policy if exists shifts_select on public.shifts;
create policy shifts_select on public.shifts
    for select using (true);

drop policy if exists shifts_insert on public.shifts;
create policy shifts_insert on public.shifts
    for insert with check (true);

drop policy if exists shifts_update on public.shifts;
create policy shifts_update on public.shifts
    for update using (true) with check (true);

drop policy if exists uang_laci_select on public.uang_laci;
create policy uang_laci_select on public.uang_laci
    for select using (true);

drop policy if exists uang_laci_insert on public.uang_laci;
create policy uang_laci_insert on public.uang_laci
    for insert with check (true);

drop policy if exists uang_laci_update on public.uang_laci;
create policy uang_laci_update on public.uang_laci
    for update using (true) with check (true);

grant select, insert, update on public.shifts to anon, authenticated;
grant select, insert, update on public.uang_laci to anon, authenticated;
grant select on public.riwayat_shift to anon, authenticated;
grant select on public.riwayat_transaksi_shift to anon, authenticated;
