package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.external.PaymentProvider
import org.junit.jupiter.api.Test
import java.util.*


class BillingServiceTest {

    private val invoiceService = mockk<InvoiceService>() {
        every { fetchAll() } returns ArrayList()
    }
    private val paymentProvider = mockk<PaymentProvider>()

    private val billingService = BillingService(
            paymentProvider,
            invoiceService,
            Calendar.SECOND,
            1,
            false)

    @Test
    fun `will call invoiceService multiple times`(){
        billingService.schedulePeriodicBilling()

        Thread.sleep(3000)

        verify(atLeast = 3, atMost = 4) { invoiceService.fetchAll() }
    }


}