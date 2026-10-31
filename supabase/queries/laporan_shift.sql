-- Rekap omzet per tanggal operasional dan shift.
-- Ganti tanggal sesuai kebutuhan.
select
    tanggal_operasional_efektif,
    jenis_shift_efektif,
    count(*) as jumlah_transaksi,
    sum(total) as omzet
from public.transaksi_shift
where paid = true
  and paket <> 'BATAL'
  and tanggal_operasional_efektif between date '2026-10-01' and date '2026-10-31'
group by 1, 2
order by 1 desc,
    case jenis_shift_efektif
        when 'Pagi' then 1
        when 'Malam' then 2
        else 3
    end;

-- Rekap per kasir dalam satu rentang tanggal.
select
    tanggal_operasional_efektif,
    jenis_shift_efektif,
    kasir,
    count(*) as jumlah_transaksi,
    sum(total) as omzet
from public.transaksi_shift
where paid = true
  and paket <> 'BATAL'
  and tanggal_operasional_efektif between date '2026-10-01' and date '2026-10-31'
group by 1, 2, 3
order by 1 desc,
    case jenis_shift_efektif
        when 'Pagi' then 1
        when 'Malam' then 2
        else 3
    end,
    kasir;

-- Detail transaksi + waktu WIB.
select
    id,
    waktu,
    waktu::timestamptz at time zone 'Asia/Jakarta' as waktu_wib,
    tanggal_operasional_efektif,
    jenis_shift_efektif,
    kasir,
    kota,
    paket,
    total,
    paid
from public.transaksi_shift
where tanggal_operasional_efektif between date '2026-10-01' and date '2026-10-31'
order by waktu desc;
