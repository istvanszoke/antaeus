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
    private val invoiceList = ArrayList<Invoice>()

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
        invoiceList.add(invoiceToBePaid1)
        invoiceList.add(invoiceToBePaid2)
        invoiceList.add(invoiceToFail1)
        invoiceList.add(invoiceToFail2)
    }

    private val invoiceService = mockk<InvoiceService> {
        every { fetchAll() } returns invoiceList
        every { update(any()) } returns Unit
    }

    private val paymentProvider = mockk<PaymentProvider> {
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

    private val paymentProviderForFails = mockk<PaymentProvider> {
        every { charge(invoiceToFail1) } returns true
    }

    private val billingService = BillingService(
            paymentProvider,
            invoiceService)

    private val billingServiceForFails = BillingService(
            paymentProviderForFails,
            invoiceService)

    @Test
    fun `will call invoiceService multiple times`() {
        billingService.scheduleInfinitePeriodicBilling(Calendar.SECOND, 1, false)

        Thread.sleep(3000)

        verify(exactly = 3) { invoiceService.fetchAll() }
    }

    @Test
    fun `will update db after payment`() {
        billingService.scheduleInfinitePeriodicBilling(Calendar.SECOND, 1, false)

        Thread.sleep(1000)

        verify(exactly = 1) { invoiceService.fetchAll() }

        verify(exactly = 1) { invoiceService.update(invoiceTobePaid1Paid) }
        verify(exactly = 1) { invoiceService.update(invoiceTobePaid2Paid) }
        verify(exactly = 1) { invoiceService.update(invoiceToFail1Failed1) }
    }

    @Test
    fun `will schedule new billing 3 times for failed payments and then falling back to manual_check`() {
        billingService.scheduleSimplePeriodicBilling()

        Thread.sleep(4000)

        verify(exactly = 1) { invoiceService.update(invoiceToFail1Failed1) }
        verify(exactly = 1) { invoiceService.update(invoiceToFail1Failed2) }
        verify(exactly = 1) { invoiceService.update(invoiceToFail1Failed3) }
        verify(exactly = 1) { invoiceService.update(invoiceToFail1Manual) }
    }

    @Test
    fun `will alert external service for MANUAL_CHECK invoices`() {

    }


}