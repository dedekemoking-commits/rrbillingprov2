-- Shift operasional global per rental.
-- Jalankan di Supabase SQL Editor. Data transaksi lama tidak di-update.

create table if not exists public.shift_settings (
    username text primary key,
    pagi_mulai time not null default '11:00'::time,
    pagi_selesai time not null default '19:00'::time,
    malam_mulai time not null default '21:00'::time,
    malam_selesai time not null default '05:00'::time,
    batas_hari time not null default '06:00'::time,
    zona_waktu text not null default 'Asia/Jakarta',
    updated_at timestamptz not null default now()
);

alter table public.transaksi
    add column if not exists tanggal_operasional date,
    add column if not exists jenis_shift text;

create index if not exists transaksi_tanggal_operasional_idx
    on public.transaksi (tanggal_operasional);
create index if not exists transaksi_jenis_shift_idx
    on public.transaksi (jenis_shift);
create index if not exists transaksi_username_tanggal_ops_idx
    on public.transaksi (username, tanggal_operasional);

create or replace function public.hitung_shift(p_waktu text, p_username text)
returns table (tanggal_operasional date, jenis_shift text)
language plpgsql
stable
as $function$
declare
    v_pagi_mulai time := '11:00'::time;
    v_pagi_selesai time := '19:00'::time;
    v_malam_mulai time := '21:00'::time;
    v_malam_selesai time := '05:00'::time;
    v_batas_hari time := '06:00'::time;
    v_zona_waktu text := 'Asia/Jakarta';
    v_waktu_lokal timestamp;
    v_tanggal_lokal date;
    v_menit int;
    v_pagi_mulai_menit int;
    v_pagi_selesai_menit int;
    v_malam_mulai_menit int;
    v_malam_selesai_menit int;
    v_batas_menit int;
    v_tanggal_ops date;
    v_shift text := 'Luar Shift';
begin
    if p_waktu is null or btrim(p_waktu) = '' then
        return query select null::date, null::text;
        return;
    end if;

    select
        coalesce(pagi_mulai, '11:00'::time),
        coalesce(pagi_selesai, '19:00'::time),
        coalesce(malam_mulai, '21:00'::time),
        coalesce(malam_selesai, '05:00'::time),
        coalesce(batas_hari, '06:00'::time),
        coalesce(nullif(btrim(zona_waktu), ''), 'Asia/Jakarta')
    into
        v_pagi_mulai, v_pagi_selesai, v_malam_mulai, v_malam_selesai,
        v_batas_hari, v_zona_waktu
    from public.shift_settings
    where username = p_username;

    if not found then
        select
            coalesce(pagi_mulai, '11:00'::time),
            coalesce(pagi_selesai, '19:00'::time),
            coalesce(malam_mulai, '21:00'::time),
            coalesce(malam_selesai, '05:00'::time),
            coalesce(batas_hari, '06:00'::time),
            coalesce(nullif(btrim(zona_waktu), ''), 'Asia/Jakarta')
        into
            v_pagi_mulai, v_pagi_selesai, v_malam_mulai, v_malam_selesai,
            v_batas_hari, v_zona_waktu
        from public.shift_settings
        order by updated_at desc
        limit 1;
    end if;

    if v_pagi_mulai is null then v_pagi_mulai := '11:00'::time; end if;
    if v_pagi_selesai is null then v_pagi_selesai := '19:00'::time; end if;
    if v_malam_mulai is null then v_malam_mulai := '21:00'::time; end if;
    if v_malam_selesai is null then v_malam_selesai := '05:00'::time; end if;
    if v_batas_hari is null then v_batas_hari := '06:00'::time; end if;
    if v_zona_waktu is null then v_zona_waktu := 'Asia/Jakarta'; end if;

    begin
        v_waktu_lokal := (p_waktu::timestamptz at time zone v_zona_waktu);
    exception when others then
        return query select null::date, null::text;
        return;
    end;

    v_tanggal_lokal := v_waktu_lokal::date;
    v_menit := extract(hour from v_waktu_lokal)::int * 60
        + extract(minute from v_waktu_lokal)::int;
    v_pagi_mulai_menit := extract(hour from v_pagi_mulai)::int * 60
        + extract(minute from v_pagi_mulai)::int;
    v_pagi_selesai_menit := extract(hour from v_pagi_selesai)::int * 60
        + extract(minute from v_pagi_selesai)::int;
    v_malam_mulai_menit := extract(hour from v_malam_mulai)::int * 60
        + extract(minute from v_malam_mulai)::int;
    v_malam_selesai_menit := extract(hour from v_malam_selesai)::int * 60
        + extract(minute from v_malam_selesai)::int;
    v_batas_menit := extract(hour from v_batas_hari)::int * 60
        + extract(minute from v_batas_hari)::int;

    if v_menit < v_batas_menit then
        v_tanggal_ops := v_tanggal_lokal - 1;
    else
        v_tanggal_ops := v_tanggal_lokal;
    end if;

    if v_menit >= v_pagi_mulai_menit and v_menit < v_pagi_selesai_menit then
        v_shift := 'Pagi';
    elsif v_malam_mulai_menit > v_malam_selesai_menit then
        if v_menit >= v_malam_mulai_menit or v_menit < v_malam_selesai_menit then
            v_shift := 'Malam';
        end if;
    elsif v_menit >= v_malam_mulai_menit and v_menit < v_malam_selesai_menit then
        v_shift := 'Malam';
    end if;

    return query select v_tanggal_ops, v_shift;
end;
$function$;

create or replace function public.set_shift_transaksi()
returns trigger
language plpgsql
as $function$
declare
    v_shift record;
begin
    -- Upsert transaksi lama tidak boleh menghitung ulang tanggal/shift historis.
    if tg_op = 'UPDATE'
        and new.waktu is not distinct from old.waktu
        and new.username is not distinct from old.username
    then
        new.tanggal_operasional := old.tanggal_operasional;
        new.jenis_shift := old.jenis_shift;
        return new;
    end if;

    select * into v_shift
    from public.hitung_shift(new.waktu::text, new.username);

    new.tanggal_operasional := v_shift.tanggal_operasional;
    new.jenis_shift := v_shift.jenis_shift;
    return new;
end;
$function$;

drop trigger if exists trg_set_shift_transaksi on public.transaksi;
create trigger trg_set_shift_transaksi
    before insert or update of waktu, username on public.transaksi
    for each row execute function public.set_shift_transaksi();

create or replace view public.transaksi_shift as
select
    t.*,
    coalesce(t.tanggal_operasional, s.tanggal_operasional) as tanggal_operasional_efektif,
    coalesce(t.jenis_shift, s.jenis_shift) as jenis_shift_efektif
from public.transaksi t
left join lateral public.hitung_shift(t.waktu::text, t.username) s on true;

alter table public.shift_settings enable row level security;

drop policy if exists shift_settings_select on public.shift_settings;
create policy shift_settings_select on public.shift_settings
    for select using (true);

drop policy if exists shift_settings_insert on public.shift_settings;
create policy shift_settings_insert on public.shift_settings
    for insert with check (true);

drop policy if exists shift_settings_update on public.shift_settings;
create policy shift_settings_update on public.shift_settings
    for update using (true) with check (true);

grant select, insert, update on public.shift_settings to anon, authenticated;
grant select on public.transaksi_shift to anon, authenticated;
grant execute on function public.hitung_shift(text, text) to anon, authenticated;
grant execute on function public.set_shift_transaksi() to anon, authenticated;

-- Contoh rekap per tanggal operasional + shift:
-- select tanggal_operasional_efektif, jenis_shift_efektif, count(*), sum(total)
-- from public.transaksi_shift
-- where paid = true
-- group by 1, 2
-- order by 1 desc, 2;
