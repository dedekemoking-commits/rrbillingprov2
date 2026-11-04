package com.billingps.aptv.models

import com.billingps.aptv.utils.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class CashDrawerEngineTest {
    private val config = ShiftConfig()
    private val rental = "rental-a"
    private val cashier = "kasir1"
    private val pagi = ZonedDateTime.of(2026, 11, 4, 12, 0, 0, 0, TimeUtils.WIB)
    private val malam = ZonedDateTime.of(2026, 11, 4, 22, 0, 0, 0, TimeUtils.WIB)

    @Test
    fun firstShiftRequiresOpeningCash() {
        val result = CashDrawerEngine.startShift(
            shifts = emptyList(),
            config = config,
            rentalUsername = rental,
            username = cashier,
            role = "kasir",
            now = pagi,
            openingCash = null,
        )

        assertNotNull(result.error)
        assertNull(result.data)
    }

    @Test
    fun nextShiftAlsoRequiresOpeningCash() {
        val first = startShift(emptyList(), 30_000)
        val closed = CashDrawerEngine.endShift(
            shifts = listOf(first.shift),
            transactions = emptyList(),
            movements = listOf(first.movement),
            shiftId = first.shift.id,
            uangFisik = 30_000,
            closedBy = cashier,
            role = "kasir",
            nowMillis = pagi.plusHours(7).toInstant().toEpochMilli(),
        ).data!!.shift

        val second = CashDrawerEngine.startShift(
            shifts = listOf(closed),
            config = config,
            rentalUsername = rental,
            username = cashier,
            role = "kasir",
            now = malam,
            openingCash = null,
        )

        assertNotNull(second.error)
        assertNull(second.data)
    }

    @Test
    fun startShiftCreatesOpeningLedgerAndRejectsSecondActiveShift() {
        val started = startShift(emptyList(), 30_000)
        assertEquals(30_000, started.shift.kasAwal)
        assertEquals(CashDrawerEngine.STATUS_AKTIF, started.shift.status)
        assertEquals(CashDrawerEngine.KATEGORI_OPENING, started.movement.kategori)
        assertEquals(30_000, started.movement.nominal)

        val second = CashDrawerEngine.startShift(
            shifts = listOf(started.shift),
            config = config,
            rentalUsername = rental,
            username = "kasir2",
            role = "kasir",
            now = pagi,
            openingCash = 10_000,
        )

        assertNotNull(second.error)
    }

    @Test
    fun cashSaleAddsTotalNotTenderedCash() {
        val shift = startShift(emptyList(), 30_000).shift
        val tx = paidTransaction("TRX-1", 170_000, "Tunai", cash = 200_000, kembalian = 30_000, at = pagi)
        val attached = attach(tx, shift, pagi)
        val metrics = CashDrawerEngine.metrics(shift, listOf(attached), emptyList(), 0)

        assertEquals(shift.id, attached.shiftId)
        assertEquals(170_000, metrics.penjualanTunai)
        assertEquals(200_000, metrics.kasSeharusnya)
        assertEquals(0, metrics.totalQris)
    }

    @Test
    fun qrisSaleDoesNotAddDrawerCash() {
        val shift = startShift(emptyList(), 30_000).shift
        val cashTx = attach(paidTransaction("TRX-2", 170_000, "Tunai", cash = 170_000, at = pagi), shift, pagi)
        val qrisTx = attach(paidTransaction("TRX-3", 50_000, "QRIS", at = pagi), shift, pagi)
        val metrics = CashDrawerEngine.metrics(shift, listOf(cashTx, qrisTx), emptyList(), 0)

        assertEquals(200_000, metrics.kasSeharusnya)
        assertEquals(50_000, metrics.totalQris)
        assertEquals(2, metrics.totalTransaksi)
    }

    @Test
    fun closeShiftCarriesActualCashToNextShift() {
        val first = startShift(emptyList(), 30_000)
        val cashTx = attach(paidTransaction("TRX-4", 170_000, "Tunai", cash = 200_000, kembalian = 30_000, at = pagi), first.shift, pagi)
        val qrisTx = attach(paidTransaction("TRX-5", 50_000, "QRIS", at = pagi), first.shift, pagi)
        val firstClose = CashDrawerEngine.endShift(
            shifts = listOf(first.shift),
            transactions = listOf(cashTx, qrisTx),
            movements = listOf(first.movement),
            shiftId = first.shift.id,
            uangFisik = 200_000,
            closedBy = cashier,
            role = "kasir",
            nowMillis = pagi.plusHours(7).toInstant().toEpochMilli(),
        )

        assertNull(firstClose.error)
        val closed = firstClose.data!!.shift
        assertEquals(200_000, closed.kasAkhir)
        assertEquals(0, closed.selisih)
        assertEquals(50_000, closed.totalQris)
        assertEquals(CashDrawerEngine.STATUS_SELESAI, closed.status)

        val second = startShift(listOf(closed), 200_000, malam)
        assertEquals(200_000, second.shift.kasAwal)
        assertEquals(closed.id, second.shift.previousShiftId)
        assertEquals(CashDrawerEngine.KATEGORI_CARRY_OVER, second.movement.kategori)

        val secondCash = attach(paidTransaction("TRX-6", 300_000, "Tunai", cash = 300_000, at = malam), second.shift, malam)
        val secondMetrics = CashDrawerEngine.metrics(second.shift, listOf(secondCash), listOf(second.movement), 0)
        assertEquals(500_000, secondMetrics.kasSeharusnya)
        assertEquals(0, secondMetrics.totalQris)
    }

    @Test
    fun nextShiftUsesEnteredOpeningCashInsteadOfPreviousClosing() {
        val first = startShift(emptyList(), 30_000)
        val closed = CashDrawerEngine.endShift(
            shifts = listOf(first.shift),
            transactions = emptyList(),
            movements = listOf(first.movement),
            shiftId = first.shift.id,
            uangFisik = 30_000,
            closedBy = cashier,
            role = "kasir",
            nowMillis = pagi.plusHours(7).toInstant().toEpochMilli(),
        ).data!!.shift

        val second = startShift(listOf(closed), 50_000, malam)
        assertEquals(50_000, second.shift.kasAwal)
        assertEquals(closed.id, second.shift.previousShiftId)
        assertEquals(CashDrawerEngine.KATEGORI_CARRY_OVER, second.movement.kategori)
    }

    @Test
    fun adminWithdrawReducesExpectedCashAndCannotExceedIt() {
        val shift = startShift(emptyList(), 500_000).shift
        val withdrawal = CashDrawerEngine.adminWithdraw(
            shifts = listOf(shift),
            transactions = emptyList(),
            movements = emptyList(),
            shiftId = shift.id,
            rentalUsername = rental,
            adminUsername = "admin",
            role = "admin",
            nominal = 450_000,
            nowMillis = pagi.toInstant().toEpochMilli(),
        )

        assertNull(withdrawal.error)
        val movement = withdrawal.data!!
        assertEquals(CashDrawerEngine.KATEGORI_ADMIN_WITHDRAW, movement.kategori)
        assertEquals("KELUAR", movement.tipe)
        assertEquals(50_000, movement.saldoSetelah)

        val metrics = CashDrawerEngine.metrics(shift, emptyList(), listOf(movement), 0)
        assertEquals(50_000, metrics.kasSeharusnya)
        assertEquals(450_000, metrics.adminWithdrawals)

        val tooMuch = CashDrawerEngine.adminWithdraw(
            shifts = listOf(shift),
            transactions = emptyList(),
            movements = listOf(movement),
            shiftId = shift.id,
            rentalUsername = rental,
            adminUsername = "admin",
            role = "admin",
            nominal = 60_000,
            nowMillis = pagi.toInstant().toEpochMilli(),
        )
        assertNotNull(tooMuch.error)
    }

    @Test
    fun cashierCannotWithdraw() {
        val shift = startShift(emptyList(), 100_000).shift
        val result = CashDrawerEngine.adminWithdraw(
            shifts = listOf(shift),
            transactions = emptyList(),
            movements = emptyList(),
            shiftId = shift.id,
            rentalUsername = rental,
            adminUsername = cashier,
            role = "kasir",
            nominal = 10_000,
            nowMillis = pagi.toInstant().toEpochMilli(),
        )

        assertNotNull(result.error)
    }

    @Test
    fun batalTransactionIsExcludedFromMetrics() {
        val shift = startShift(emptyList(), 30_000).shift
        val tx = paidTransaction("TRX-8", -50_000, "BATAL", at = pagi)
            .copy(paket = "BATAL: PS3", kategori = "BATAL")
        val attached = attach(tx, shift, pagi)
        val metrics = CashDrawerEngine.metrics(shift, listOf(attached), emptyList(), 0)

        assertEquals(0, metrics.totalTransaksi)
        assertEquals(0, metrics.totalQris)
        assertEquals(0, metrics.penjualanTunai)
        assertEquals(30_000, metrics.kasSeharusnya)
    }

    @Test
    fun unpaidTransactionHasNoShiftId() {
        val shift = startShift(emptyList(), 30_000).shift
        val tx = Transaksi(id = "TRX-7", waktu = TimeUtils.toUtcIso(pagi.toInstant()), kasir = cashier, total = 50_000)
        val result = CashDrawerEngine.addTransaction(tx, listOf(shift), config, rental, cashier, pagi)

        assertNull(result.error)
        assertEquals("", result.data!!.shiftId)
    }

    @Test
    fun closingShiftRejectsNonOwnerNonAdmin() {
        val shift = startShift(emptyList(), 30_000).shift
        val result = CashDrawerEngine.endShift(
            shifts = listOf(shift),
            transactions = emptyList(),
            movements = emptyList(),
            shiftId = shift.id,
            uangFisik = 30_000,
            closedBy = "kasir2",
            role = "kasir",
            nowMillis = pagi.toInstant().toEpochMilli(),
        )

        assertNotNull(result.error)
    }

    @Test
    fun activeShiftIsGlobalPerRental() {
        val first = startShift(emptyList(), 30_000)
        val otherRental = CashDrawerEngine.startShift(
            shifts = listOf(first.shift),
            config = config,
            rentalUsername = "rental-b",
            username = "kasir2",
            role = "kasir",
            now = pagi,
            openingCash = 10_000,
        )

        assertNull(otherRental.error)
        assertTrue(otherRental.data!!.shift.rentalUsername == "rental-b")
    }

    private fun startShift(
        shifts: List<ShiftKasir>,
        openingCash: Int?,
        at: ZonedDateTime = pagi,
    ): ShiftStartResult {
        val result = CashDrawerEngine.startShift(
            shifts = shifts,
            config = config,
            rentalUsername = rental,
            username = cashier,
            role = "kasir",
            now = at,
            openingCash = openingCash,
        )
        assertNull(result.error)
        return result.data!!
    }

    private fun attach(tx: Transaksi, shift: ShiftKasir, at: ZonedDateTime): Transaksi {
        val result = CashDrawerEngine.addTransaction(
            tx = tx,
            shifts = listOf(shift),
            config = config,
            rentalUsername = rental,
            username = cashier,
            now = at,
        )
        assertNull(result.error)
        return result.data!!
    }

    private fun paidTransaction(
        id: String,
        total: Int,
        metode: String,
        cash: Int = 0,
        kembalian: Int = 0,
        at: ZonedDateTime,
    ): Transaksi {
        val iso = TimeUtils.toUtcIso(at.toInstant())
        return Transaksi(
            id = id,
            waktu = iso,
            kasir = cashier,
            kota = "PS1",
            paket = "PS3",
            total = total,
            sudahBayar = true,
            metode = metode,
            cash = cash,
            kembalian = kembalian,
            waktuBayar = iso,
        )
    }
}
