package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*


class BillingServiceTest {

    private val billingService : BillingService
    private val invoiceService : InvoiceService
    private val paymentProvider : PaymentProvider

    private val invoiceListWhenPending = ArrayList<Invoice>()
    private val invoiceListWhenFailed1 = ArrayList<Invoice>()
    private val invoiceListWhenFailed2 = ArrayList<Invoice>()
    private val invoiceListWhenFailed3 = ArrayList<Invoice>()

    private val invoiceToBePaid1 = Invoice(1, 1, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
    private val invoiceTobePaid1Paid = invoiceToBePaid1.copy(status = InvoiceStatus.PAID)

    private val invoiceToBePaid2 = Invoice(2, 1, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
    private val invoiceTobePaid2Paid = invoiceToBePaid2.copy(status = InvoiceStatus.PAID)

    private val invoiceToFail1 = Invoice(3, 1, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
    private val invoiceToFail1Failed1 = invoiceToFail1.copy(status = InvoiceStatus.FAILED1)
    private val invoiceToFail1Failed2 = invoiceToFail1.copy(status = InvoiceStatus.FAILED2)
    private val invoiceToFail1Failed3 = invoiceToFail1.copy(status = InvoiceStatus.FAILED3)
    private val invoiceToFail1Manual = invoiceToFail1.copy(status = InvoiceStatus.MANUAL_CHECK)

    private val invoiceToFail2 = Invoice(4, 1, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
    private val invoiceToFail2Failed1 = invoiceToFail2.copy(status = InvoiceStatus.FAILED1)
    private val invoiceToFail2Failed2 = invoiceToFail2.copy(status = InvoiceStatus.FAILED2)
    private val invoiceToFail2Failed3 = invoiceToFail2.copy(status = InvoiceStatus.FAILED3)

    init {
        invoiceListWhenPending.add(invoiceToBePaid1)
        invoiceListWhenPending.add(invoiceToBePaid2)
        invoiceListWhenPending.add(invoiceToFail1)
        invoiceListWhenPending.add(invoiceToFail2)

        invoiceListWhenFailed1.add(invoiceToFail1Failed1)
        invoiceListWhenFailed1.add(invoiceToFail2Failed1)

        invoiceListWhenFailed2.add(invoiceToFail1Failed2)
        invoiceListWhenFailed2.add(invoiceToFail2Failed2)

        invoiceListWhenFailed3.add(invoiceToFail1Failed3)
        invoiceListWhenFailed3.add(invoiceToFail2Failed3)

        invoiceService = mockk {
            every { fetchOfStatus(InvoiceStatus.PENDING) } returns invoiceListWhenPending
            every { fetchOfStatus(InvoiceStatus.FAILED1) } returns invoiceListWhenFailed1
            every { fetchOfStatus(InvoiceStatus.FAILED2) } returns invoiceListWhenFailed2
            every { fetchOfStatus(InvoiceStatus.FAILED3) } returns invoiceListWhenFailed3

            every { update(any()) } returns Unit
        }

        paymentProvider = mockk {
            every { charge(invoiceToBePaid1) } returns true

            every { charge(invoiceToBePaid2) } returns true

            every { charge(invoiceToFail1) } returns false
            every { charge(invoiceToFail1Failed1) } returns false
            every { charge(invoiceToFail1Failed2) } returns false
            every { charge(invoiceToFail1Failed3) } returns false

            every { charge(invoiceToFail2) } returns false
            every { charge(invoiceToFail2Failed1) } returns false
            every { charge(invoiceToFail2Failed2) } returns true
            every { charge(invoiceToFail2Failed3) } returns true
        }

        billingService = BillingService(
                paymentProvider,
                invoiceService,
                Calendar.SECOND,
                5,
                Calendar.SECOND,
                1,
                false)
    }

    @Test
    fun `will call invoiceService multiple times`() {
        billingService.scheduleNext()

        Thread.sleep(10000)

        verify(exactly = 1) { invoiceService.fetchOfStatus(InvoiceStatus.PENDING) }
        verify(exactly = 1) { invoiceService.fetchOfStatus(InvoiceStatus.FAILED1) }
        verify(exactly = 1) { invoiceService.fetchOfStatus(InvoiceStatus.FAILED2) }
        verify(exactly = 1) { invoiceService.fetchOfStatus(InvoiceStatus.FAILED3) }
    }

    @Test
    fun `will update db after payment`() {
        billingService.scheduleNext()

        Thread.sleep(5000)

        verify(exactly = 1) { invoiceService.fetchOfStatus(InvoiceStatus.PENDING) }

        verify(exactly = 1) { invoiceService.update(invoiceTobePaid1Paid) }
        verify(exactly = 1) { invoiceService.update(invoiceTobePaid2Paid) }
        verify(exactly = 1) { invoiceService.update(invoiceToFail1Failed1) }
        verify(exactly = 1) { invoiceService.update(invoiceToFail2Failed1) }
    }

    @Test
    fun `will schedule new billing 3 times for failed payments and then falling back to manual_check`() {
        billingService.scheduleNext()

        Thread.sleep(10000)

        verify(exactly = 1) { invoiceService.update(invoiceToFail1Failed1) }
        verify(exactly = 1) { invoiceService.update(invoiceToFail1Failed2) }
        verify(exactly = 1) { invoiceService.update(invoiceToFail1Failed3) }
        verify(exactly = 1) { invoiceService.update(invoiceToFail1Manual) }
    }

    @Test
    fun `will alert external service for MANUAL_CHECK invoices`() {

    }

//TODO add error cases
}