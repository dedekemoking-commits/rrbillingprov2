-- Perbaikan trigger shift: gunakan pemilik rental dari shift_id,
-- bukan username kasir, agar shift_settings multi-rental tetap benar.

create or replace function public.set_shift_transaksi()
returns trigger
language plpgsql
as $function$
declare
    v_shift record;
    v_shift_owner text;
begin
    -- Pertahankan hasil historis selama waktu, username, dan shift tidak berubah.
    -- Jika hasil lama masih null, hitung ulang agar transaksi lama bisa terisi.
    if tg_op = 'UPDATE'
        and new.waktu is not distinct from old.waktu
        and new.username is not distinct from old.username
        and new.shift_id is not distinct from old.shift_id
        and old.tanggal_operasional is not null
        and old.jenis_shift is not null
    then
        new.tanggal_operasional := old.tanggal_operasional;
        new.jenis_shift := old.jenis_shift;
        return new;
    end if;

    -- Prioritas konfigurasi: pemilik rental dari shift yang terhubung.
    if new.shift_id is not null and btrim(new.shift_id) <> '' then
        select s.rental_username
        into v_shift_owner
        from public.shifts s
        where s.id = new.shift_id
        limit 1;
    end if;

    if v_shift_owner is null or btrim(v_shift_owner) = '' then
        v_shift_owner := new.username;
    end if;

    select * into v_shift
    from public.hitung_shift(new.waktu::text, v_shift_owner);

    new.tanggal_operasional := v_shift.tanggal_operasional;
    new.jenis_shift := v_shift.jenis_shift;
    return new;
end;
$function$;

drop trigger if exists trg_set_shift_transaksi on public.transaksi;
create trigger trg_set_shift_transaksi
    before insert or update of waktu, username, shift_id on public.transaksi
    for each row execute function public.set_shift_transaksi();
