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
    private val invoiceToBePaid2 = Invoice(2, 1, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)

    private val invoiceToFail = Invoice(3, 1, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)

    init {
        invoiceList.add(invoiceToBePaid1)
        invoiceList.add(invoiceToBePaid2)
        invoiceList.add(invoiceToFail)
    }

    private val invoiceService = mockk<InvoiceService> {
        every { fetchAll() } returns invoiceList
        every { update(any()) } returns Unit
    }

    private val paymentProvider = mockk<PaymentProvider> {
        every { charge(invoiceToBePaid1) } returns true
        every { charge(invoiceToBePaid2) } returns true
        every { charge(invoiceToFail) } returns false
    }

    private val paymentProviderForFails = mockk<PaymentProvider> {
        every { charge(invoiceToFail) } returns true
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

        val invoice1 = invoiceToBePaid1.copy(status = InvoiceStatus.PAID)
        val invoice2 = invoiceToBePaid2.copy(status = InvoiceStatus.PAID)
        val invoice3 = invoiceToFail.copy(status = InvoiceStatus.FAILED)
        verify(exactly = 1) { invoiceService.update(invoice1) }
        verify(exactly = 1) { invoiceService.update(invoice2) }
        verify(exactly = 1) { invoiceService.update(invoice3) }
    }

    @Test
    fun `will schedule new billing for failed payments`() {

    }

    @Test
    fun `will alert external service for MANUAL_CHECK invoices`() {

    }


}