package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class InvoiceServiceTest {
    private val invoice1 = Invoice(1, 1, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
    private val invoice2 = Invoice(2, 1, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.PENDING)
    private val invoice3 = Invoice(3, 1, Money(BigDecimal.ONE, Currency.EUR), InvoiceStatus.FAILED1)

    private val invoiceList = ArrayList<Invoice>()

    private val dal = mockk<AntaeusDal> {
        every { fetchInvoice(404) } returns null
        every { fetchInvoice(1) } returns invoice1
        every { fetchInvoiceOfStatus(InvoiceStatus.PENDING) } returns invoiceList
    }

    private val invoiceService = InvoiceService(dal = dal)

    init {
        invoiceList.add(invoice1)
        invoiceList.add(invoice2)
    }

    @Test
    fun `will throw if customer is not found`() {
        assertThrows<InvoiceNotFoundException> {
            invoiceService.fetch(404)
        }
    }

    @Test
    fun `will return invoice by id`() {
        val actual = dal.fetchInvoice(1)

        Assertions.assertEquals(invoice1, actual)
    }

    @Test
    fun `will return invoice by status`() {
        val actual = dal.fetchInvoiceOfStatus(InvoiceStatus.PENDING)

        Assertions.assertEquals(invoiceList, actual)
    }
}